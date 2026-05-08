package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.domain.Player;
import fc.ul.scrimfinder.domain.PlayerRanking;
import fc.ul.scrimfinder.domain.QueueEntity;
import fc.ul.scrimfinder.domain.RiotAccount;
import fc.ul.scrimfinder.dto.internal.HistoryAddMatchEvent;
import fc.ul.scrimfinder.dto.request.CreatePlayerRequest;
import fc.ul.scrimfinder.dto.request.MatchResultRequest;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.dto.response.PlayerRankingDTO;
import fc.ul.scrimfinder.exception.LeagueAccountNotLinkedException;
import fc.ul.scrimfinder.exception.MMRAlreadyExistsException;
import fc.ul.scrimfinder.exception.PlayerNotFoundException;
import fc.ul.scrimfinder.exception.QueueNotFoundException;
import fc.ul.scrimfinder.mapper.PlayerRankingMapper;
import fc.ul.scrimfinder.repository.PlayerRankingRepository;
import fc.ul.scrimfinder.repository.PlayerRepository;
import fc.ul.scrimfinder.repository.QueueRepository;
import fc.ul.scrimfinder.repository.ReplicaPlayerRankingReadRepository;
import fc.ul.scrimfinder.repository.RiotAccountRepository;
import fc.ul.scrimfinder.rest.client.ExternalGameClient;
import fc.ul.scrimfinder.service.PlayerRankingService;
import fc.ul.scrimfinder.util.Region;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Slf4j
@ApplicationScoped
public class PlayerRankingServiceImpl implements PlayerRankingService {

    @Inject PlayerRankingRepository playerRankingRepository;

    @Inject ReplicaPlayerRankingReadRepository replicaReadRepository;

    @Inject PlayerRepository playerRepository;
    @Inject QueueRepository queueRepository;
    @Inject RiotAccountRepository riotAccountRepository;
    @Inject PlayerRankingMapper playerRankingMapper;

    @Inject @RestClient ExternalGameClient externalGameClient;

    @Inject
    @Channel("history-add-match-events")
    Emitter<String> historyAddMatchEmitter;

    @Inject com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private static final int DEFAULT_INITIAL_MMR = 1000;

    @Override
    @Transactional
    @Retry(maxRetries = 3, delay = 1000)
    @Timeout(10000)
    @CircuitBreaker(requestVolumeThreshold = 4)
    public Map<UUID, PlayerRankingDTO> processMatchResults(MatchResultRequest request) {
        log.info(
                "\u001B[33m[PENDING]\u001B[0m Processing match results for game {} in queue {}",
                request.gameId(),
                request.queueId());

        QueueEntity queue =
                queueRepository
                        .findByIdOptional(request.queueId())
                        .orElseThrow(
                                () -> {
                                    log.error(
                                            "\u001B[31m[ERROR]\u001B[0m Queue {} not found during match processing",
                                            request.queueId());
                                    return new QueueNotFoundException("Queue not found: " + request.queueId());
                                });

        log.info(
                "\u001B[34m[INFO]\u001B[0m Fetching official match results from External API for {}",
                request.gameId());
        var matchResult = externalGameClient.fetchMatchResult(request.gameId());

        Map<String, Boolean> puuidWonStatus = new HashMap<>();
        if (matchResult != null && matchResult.players() != null) {
            matchResult
                    .players()
                    .forEach(
                            p -> {
                                if (p.riotId() != null && p.riotId().puuid() != null) {
                                    puuidWonStatus.put(p.riotId().puuid(), p.won());
                                }
                            });
        }

        Map<UUID, PlayerRankingDTO> results = new HashMap<>();
        Map<String, Integer> stringDeltas = new HashMap<>();

        log.info(
                "\u001B[34m[INFO]\u001B[0m Calculating MMR updates for {} participants",
                request.playerDeltas().size());
        for (Map.Entry<String, MatchResultRequest.PlayerDelta> entry :
                request.playerDeltas().entrySet()) {
            String puuid = entry.getKey();
            MatchResultRequest.PlayerDelta deltas = entry.getValue();

            RiotAccount riotAccount =
                    riotAccountRepository
                            .findByPuuidOptional(puuid)
                            .orElseThrow(
                                    () -> {
                                        log.error(
                                                "\u001B[31m[ERROR]\u001B[0m Riot account {} not found during match processing",
                                                puuid);
                                        return new PlayerNotFoundException("Riot account not found: " + puuid);
                                    });

            Player player = riotAccount.getPlayer();
            Boolean won = puuidWonStatus.get(puuid);
            if (won == null) {
                log.warn(
                        "\u001B[33m[WARN]\u001B[0m PUUID {} not found in external match result {}. Skipping.",
                        puuid,
                        request.gameId());
                continue;
            }

            int appliedDelta = won ? deltas.winDelta() : -deltas.lossDelta();

            updatePlayerMMRInternal(player.getId(), queue, appliedDelta, won, results);
            stringDeltas.put(puuid, appliedDelta);
        }

        try {
            log.info(
                    "\u001B[34m[INFO]\u001B[0m MMR calculations complete. Publishing history sync event via RabbitMQ...");
            HistoryAddMatchEvent event =
                    new HistoryAddMatchEvent(request.gameId(), request.queueId().toString(), stringDeltas);
            historyAddMatchEmitter.send(objectMapper.writeValueAsString(event));
            log.info(
                    "\u001B[32m[SUCCESS]\u001B[0m Match {} results successfully propagated to Ranking and queued for History Service",
                    request.gameId());
        } catch (Exception failure) {
            log.error(
                    "\u001B[31m[ERROR]\u001B[0m RabbitMQ publish failure for History Service: {}",
                    failure.getMessage());
            throw new RuntimeException(
                    "Failed to publish match results event to History Service", failure);
        }

        return results;
    }

    private void updatePlayerMMRInternal(
            UUID playerId,
            QueueEntity queue,
            int delta,
            boolean isWin,
            Map<UUID, PlayerRankingDTO> results) {
        Player player =
                playerRepository
                        .findByIdOptional(playerId)
                        .orElseThrow(() -> new PlayerNotFoundException("Player " + playerId + " not found"));

        PlayerRanking ranking =
                playerRankingRepository
                        .findByPlayerAndQueue(player, queue)
                        .orElseThrow(
                                () -> {
                                    log.error(
                                            "\u001B[31m[ERROR]\u001B[0m Ranking record missing for player {} in queue {}",
                                            playerId,
                                            queue.getId());
                                    return new MMRAlreadyExistsException(
                                            "Ranking not found for player " + playerId + " in this queue");
                                });

        int oldMmr = ranking.getMmr();
        ranking.setMmr(oldMmr + delta);
        if (isWin) {
            ranking.setWins(ranking.getWins() + 1);
        } else {
            ranking.setLosses(ranking.getLosses() + 1);
        }

        playerRankingRepository.persist(ranking);
        results.put(playerId, playerRankingMapper.toDTO(ranking));

        log.info(
                "\u001B[32m[STATE CHANGE]\u001B[0m Ranking Update | Player: {} | Queue: {} | Outcome: {} | MMR: {} -> {} ({}{})",
                playerId,
                queue.getId(),
                (isWin ? "WIN" : "LOSS"),
                oldMmr,
                ranking.getMmr(),
                (delta >= 0 ? "+" : ""),
                delta);
    }

    @Override
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public PaginatedResponseDTO<PlayerRankingDTO> getQueueLeaderboard(
            int page, int size, Optional<UUID> queueId, Optional<Region> region) {
        Optional<QueueEntity> queue =
                queueId.map(
                        id ->
                                queueRepository
                                        .findByIdOptional(id)
                                        .orElseThrow(() -> new QueueNotFoundException("Queue not found: " + id)));
        log.info("\u001B[34m[INFO]\u001B[0m Fetching leaderboard (from Read Replica)");
        try {
            return replicaReadRepository.findLeaderboard(
                    page, size, queue.map(QueueEntity::getId), region);
        } catch (Exception replicaFailure) {
            log.warn(
                    "\u001B[33m[WARN]\u001B[0m Replica read failed for leaderboard, falling back to primary: {}",
                    replicaFailure.getMessage());
            return playerRankingRepository.findLeaderboard(page, size, queue, region);
        }
    }

    @Override
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public List<PlayerRankingDTO> getPlayerRanking(UUID playerId, Optional<UUID> queueId) {
        log.info(
                "\u001B[34m[INFO]\u001B[0m GET player ranking (from Read Replica) for player: {}",
                playerId);
        try {
            if (queueId.isPresent()) {
                return replicaReadRepository.findByPlayerAndQueue(playerId, queueId.get());
            }
            return replicaReadRepository.findByPlayerId(playerId);
        } catch (Exception replicaFailure) {
            log.warn(
                    "\u001B[33m[WARN]\u001B[0m Replica read failed for player ranking, falling back to primary: {}",
                    replicaFailure.getMessage());
            if (queueId.isPresent()) {
                return playerRankingRepository.findByPlayerAndQueue(playerId, queueId.get()).stream()
                        .map(playerRankingMapper::toDTO)
                        .collect(Collectors.toList());
            }
            return playerRankingRepository.findByPlayerId(playerId).stream()
                    .map(playerRankingMapper::toDTO)
                    .collect(Collectors.toList());
        }
    }

    @Override
    @Transactional
    public PlayerRankingDTO populatePlayerMMR(UUID playerId, CreatePlayerRequest request) {
        Player player =
                playerRepository
                        .findByIdOptional(playerId)
                        .orElseThrow(() -> new PlayerNotFoundException("Player " + playerId + " not found"));

        QueueEntity queue =
                request
                        .queueId()
                        .flatMap(queueRepository::findByIdOptional)
                        .orElseThrow(() -> new QueueNotFoundException("Queue not found"));

        if (player.getRiotAccounts().isEmpty()) {
            throw new LeagueAccountNotLinkedException("Player must have a linked Riot account first");
        }

        Optional<PlayerRanking> existing = playerRankingRepository.findByPlayerAndQueue(player, queue);
        if (existing.isPresent()) {
            throw new MMRAlreadyExistsException("MMR already exists for this player in this queue");
        }

        int initialMmr =
                switch (queue.getMmrRuleType()) {
                    case SOLOQ_RANK -> player.getSoloqMMR();
                    case FLEX_RANK -> player.getFlexMMR();
                    default -> queue.getInitialMMR();
                };

        PlayerRanking ranking = new PlayerRanking();
        ranking.setPlayer(player);
        ranking.setQueue(queue);
        ranking.setMmr(initialMmr);

        playerRankingRepository.persist(ranking);
        return playerRankingMapper.toDTO(ranking);
    }
}
