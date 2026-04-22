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
import fc.ul.scrimfinder.util.Region;
import io.quarkus.grpc.GrpcClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Slf4j
@ApplicationScoped
public class PlayerRankingServiceImpl implements PlayerRankingService {

    @Inject PlayerRankingRepository playerRankingRepository;
    @Inject PlayerRepository playerRepository;
    @Inject QueueRepository queueRepository;
    @Inject RiotAccountRepository riotAccountRepository;
    @Inject PlayerRankingMapper playerRankingMapper;

    @Inject @RestClient ExternalGameClient externalGameClient;

    @Inject
    @GrpcClient("history-service")
    MatchHistoryService matchHistoryService;

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

        log.info("\u001B[34m[INFO]\u001B[0m Fetching official match results from External API for {}", request.gameId());
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

        log.info("\u001B[34m[INFO]\u001B[0m Calculating MMR updates for {} participants", request.playerDeltas().size());
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

        log.info("\u001B[34m[INFO]\u001B[0m MMR calculations complete. Syncing with History Service via gRPC...");
        SaveMatchMMRGainsRequest gRpcRequest =
                SaveMatchMMRGainsRequest.newBuilder()
                        .setGameId(request.gameId())
                        .setQueueId(request.queueId().toString())
                        .putAllPlayerMMRGains(stringDeltas)
                        .build();

        try {
            var response = matchHistoryService.saveMatchMMRGains(gRpcRequest).await().atMost(Duration.ofSeconds(5));
            if (response.getSuccess()) {
                log.info(
                        "\u001B[32m[SUCCESS]\u001B[0m Match {} results successfully propagated to Ranking and History Services",
                        request.gameId());
            } else {
                log.error(
                        "\u001B[31m[ERROR]\u001B[0m History Service failed to save results: {}",
                        response.getMessage());
            }
        } catch (Exception failure) {
            log.error(
                    "\u001B[31m[ERROR]\u001B[0m gRPC communication failure with History Service: {}",
                    failure.getMessage());
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
    @Transactional
    public PaginatedResponseDTO<PlayerRankingDTO> getQueueLeaderboard(
            int page, int size, Optional<UUID> queueId, Optional<Region> region) {
        Optional<QueueEntity> queue = queueId.map(id ->
                queueRepository.findByIdOptional(id)
                        .orElseThrow(() -> new QueueNotFoundException("Queue not found: " + id))
        );
        return playerRankingRepository.findLeaderboard(page, size, queue, region);
    }

    @Override
    @Transactional
    public List<PlayerRankingDTO> getPlayerRanking(UUID playerId, Optional<UUID> queueId) {
        Player player =
                playerRepository
                        .findByIdOptional(playerId)
                        .orElseThrow(() -> new PlayerNotFoundException("Player " + playerId + " not found"));

        if (queueId.isPresent()) {
            QueueEntity queue =
                    queueRepository
                            .findByIdOptional(queueId.get())
                            .orElseThrow(() -> new QueueNotFoundException("Queue not found: " + queueId.get()));

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
    public PlayerRankingDTO populatePlayerMMR(UUID playerId, CreatePlayerRequest request) {
        Player player =
                playerRepository
                        .findByIdOptional(playerId)
                        .orElseThrow(() -> new PlayerNotFoundException("Player " + playerId + " not found"));

        QueueEntity queue =
                request.queueId().flatMap(queueRepository::findByIdOptional)
                        .orElseThrow(() -> new QueueNotFoundException("Queue not found"));

        Optional<PlayerRanking> existing = playerRankingRepository.findByPlayerAndQueue(player, queue);
        if (existing.isPresent()) {
            return playerRankingMapper.toDTO(existing.get());
        }

        int initialMmr = DEFAULT_INITIAL_MMR;
        
        PlayerRanking ranking = new PlayerRanking();
        ranking.setPlayer(player);
        ranking.setQueue(queue);
        ranking.setMmr(initialMmr);

        playerRankingRepository.persist(ranking);
        return playerRankingMapper.toDTO(ranking);
    }
}
