package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.domain.Player;
import fc.ul.scrimfinder.domain.PlayerRanking;
import fc.ul.scrimfinder.domain.QueueEntity;
import fc.ul.scrimfinder.domain.RiotAccount;
import fc.ul.scrimfinder.dto.request.CreatePlayerRequest;
import fc.ul.scrimfinder.dto.request.MatchResultRequest;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.dto.response.PlayerRankingDTO;
import fc.ul.scrimfinder.exception.LeagueAccountNotLinkedException;
import fc.ul.scrimfinder.exception.MMRAlreadyExistsException;
import fc.ul.scrimfinder.exception.PlayerNotFoundException;
import fc.ul.scrimfinder.exception.QueueNotFoundException;
import fc.ul.scrimfinder.grpc.MatchHistoryService;
import fc.ul.scrimfinder.grpc.SaveMatchMMRGainsRequest;
import fc.ul.scrimfinder.mapper.PlayerRankingMapper;
import fc.ul.scrimfinder.repository.PlayerRankingRepository;
import fc.ul.scrimfinder.repository.PlayerRepository;
import fc.ul.scrimfinder.repository.QueueRepository;
import fc.ul.scrimfinder.repository.RiotAccountRepository;
import fc.ul.scrimfinder.rest.client.ExternalGameClient;
import fc.ul.scrimfinder.service.PlayerRankingService;
import fc.ul.scrimfinder.util.MMRRuleType;
import fc.ul.scrimfinder.util.Region;
import io.quarkus.grpc.GrpcClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
public class PlayerRankingServiceImpl implements PlayerRankingService {

    @Inject PlayerRankingRepository playerRankingRepository;

    @Inject PlayerRepository playerRepository;

    @Inject QueueRepository queueRepository;

    @Inject
    RiotAccountRepository riotAccountRepository;

    @Inject PlayerRankingMapper playerRankingMapper;

    @Inject
    @GrpcClient("history-service")
    MatchHistoryService matchHistoryService;

    @Inject @RestClient ExternalGameClient externalGameClient;

    @Override
    @Retry(maxRetries = 2, delay = 500)
    @Timeout(2000)
    public List<PlayerRankingDTO> getPlayerRanking(UUID playerId, Optional<UUID> queueId) {
        Player player =
                playerRepository
                        .findByIdOptional(playerId)
                        .orElseThrow(
                                () -> {
                                    log.error("\u001B[31m[ERROR]\u001B[0m Player not found: {}", playerId);
                                    return new PlayerNotFoundException("Player not found");
                                });

        if (queueId.isPresent()) {
            QueueEntity queue =
                    queueRepository
                            .findByIdOptional(queueId.get())
                            .orElseThrow(
                                    () -> {
                                        log.error("\u001B[31m[ERROR]\u001B[0m Queue not found: {}", queueId.get());
                                        return new QueueNotFoundException("Queue not found");
                                    });
            return playerRankingRepository
                    .findByPlayerAndQueue(player, queue)
                    .map(ranking -> List.of(playerRankingMapper.toDTO(ranking)))
                    .orElse(Collections.emptyList());
        }

        return playerRankingRepository.find("player", player).list().stream()
                .map(playerRankingMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @Retry(maxRetries = 2, delay = 500)
    @Timeout(5000)
    @CircuitBreaker(requestVolumeThreshold = 5, failureRatio = 0.6, delay = 5000)
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
                                            "\u001B[31m[ERROR]\u001B[0m Queue {} not found for match results",
                                            request.queueId());
                                    return new QueueNotFoundException("Queue not found");
                                });

        var matchResult = externalGameClient.fetchMatchResult(request.gameId());

        // Map puuid to won status from external API
        Map<String, Boolean> puuidWonStatus = new HashMap<>();
        if (matchResult.players() != null) {
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
        Map<UUID, Integer> finalDeltas = new HashMap<>();

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

            updatePlayerMMRInternal(player.getId(), queue, appliedDelta, won, results, finalDeltas);
        }

        Map<String, Integer> stringDeltas = new HashMap<>();
        finalDeltas.forEach((id, delta) -> stringDeltas.put(id.toString(), delta));

        SaveMatchMMRGainsRequest gRpcRequest =
                SaveMatchMMRGainsRequest.newBuilder()
                        .setGameId(request.gameId())
                        .setQueueId(request.queueId().toString())
                        .putAllPlayerMMRGains(stringDeltas)
                        .build();

        matchHistoryService
                .saveMatchMMRGains(gRpcRequest)
                .subscribe()
                .with(
                        response -> {
                            if (response.getSuccess()) {
                                log.info(
                                        "\u001B[32m[STATE CHANGE]\u001B[0m MMR gains for game {} saved to History Service",
                                        request.gameId());
                            } else {
                                log.error(
                                        "\u001B[31m[ERROR]\u001B[0m History Service failed to save MMR gains for game {}: {}",
                                        request.gameId(),
                                        response.getMessage());
                            }
                        },
                        failure -> {
                            log.error(
                                    "\u001B[31m[ERROR]\u001B[0m Failed to communicate with History Service for game {}: {}",
                                    request.gameId(),
                                    failure.getMessage());
                        });

        return results;
    }

    private void updatePlayerMMRInternal(
            UUID playerId,
            QueueEntity queue,
            int delta,
            boolean isWin,
            Map<UUID, PlayerRankingDTO> results,
            Map<UUID, Integer> playerDeltas) {
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
                                            "\u001B[31m[ERROR]\u001B[0m Ranking record not found for player {} in queue {}",
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
        playerDeltas.put(playerId, delta);

        log.info(
                "\u001B[32m[STATE CHANGE]\u001B[0m MMR Update: Player {} (Queue {}): {} -> {} ({}{})",
                playerId,
                queue.getId(),
                oldMmr,
                ranking.getMmr(),
                (delta >= 0 ? "+" : ""),
                delta);
    }

    @Override
    public PaginatedResponseDTO<PlayerRankingDTO> getQueueLeaderboard(
            int page, int size, Optional<UUID> queueId, Optional<Region> region) {
        if (queueId.isPresent()) {
            QueueEntity queue =
                    queueRepository
                            .findByIdOptional(queueId.get())
                            .orElseThrow(() -> new QueueNotFoundException("Queue not found"));
            return playerRankingRepository.findLeaderboard(page, size, Optional.of(queue), region);
        }
        return playerRankingRepository.findLeaderboard(page, size, Optional.empty(), region);
    }

    @Override
    @Transactional
    public PlayerRankingDTO populatePlayerMMR(
            UUID playerId, CreatePlayerRequest createPlayerRequest) {
        Player player =
                playerRepository
                        .findByIdOptional(playerId)
                        .orElseThrow(() -> new PlayerNotFoundException("Player not found"));

        QueueEntity queue =
                queueRepository
                        .findByIdOptional(createPlayerRequest.queueId().get())
                        .orElseThrow(() -> new QueueNotFoundException("Queue not found"));

        if (playerRankingRepository.findByPlayerAndQueue(player, queue).isPresent()) {
            throw new MMRAlreadyExistsException("MMR already exists for this player in this queue");
        }

        if (player.getPrimaryAccount() == null) {
            throw new LeagueAccountNotLinkedException("Player has no linked League of Legends account");
        }

        int initialMmr = queue.getInitialMMR();
        if (queue.getMmrRuleType() == MMRRuleType.SOLOQ_RANK) {
            initialMmr = player.getSoloqMMR();
        } else if (queue.getMmrRuleType() == MMRRuleType.FLEX_RANK) {
            initialMmr = player.getFlexMMR();
        }

        PlayerRanking ranking = new PlayerRanking();
        ranking.setPlayer(player);
        ranking.setQueue(queue);
        ranking.setMmr(initialMmr);

        playerRankingRepository.persist(ranking);
        return playerRankingMapper.toDTO(ranking);
    }
}
