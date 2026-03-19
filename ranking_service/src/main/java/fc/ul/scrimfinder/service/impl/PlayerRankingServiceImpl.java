package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.domain.Player;
import fc.ul.scrimfinder.domain.PlayerRanking;
import fc.ul.scrimfinder.domain.QueueEntity;
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
import fc.ul.scrimfinder.rest.client.ExternalGameClient;
import fc.ul.scrimfinder.service.PlayerRankingService;
import fc.ul.scrimfinder.util.Region;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class PlayerRankingServiceImpl implements PlayerRankingService {

    @Inject PlayerRankingRepository playerRankingRepository;

    @Inject PlayerRepository playerRepository;

    @Inject QueueRepository queueRepository;

    @Inject PlayerRankingMapper playerRankingMapper;

    @Inject
    @GrpcClient("history-service")
    MatchHistoryService matchHistoryService;

    @Inject @RestClient ExternalGameClient externalGameClient;

    @Override
    public List<PlayerRankingDTO> getPlayerRanking(Long playerId, Optional<Long> queueId) {
        Player player =
                playerRepository
                        .findByIdOptional(playerId)
                        .orElseThrow(() -> new PlayerNotFoundException("Player not found"));

        if (queueId.isPresent()) {
            QueueEntity queue =
                    queueRepository
                            .findByIdOptional(queueId.get())
                            .orElseThrow(() -> new QueueNotFoundException("Queue not found"));
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
    public Map<Long, PlayerRankingDTO> processMatchResults(MatchResultRequest request) {
        QueueEntity queue =
                queueRepository
                        .findByIdOptional(request.queueId())
                        .orElseThrow(() -> new QueueNotFoundException("Queue not found"));

        var matchResult = externalGameClient.fetchMatchResult(request.gameId());
        Set<Long> winners = new HashSet<>(matchResult.winners());
        Set<Long> losers = new HashSet<>(matchResult.losers());

        Map<Long, PlayerRankingDTO> results = new HashMap<>();
        Map<Long, Integer> finalDeltas = new HashMap<>();

        for (Map.Entry<Long, MatchResultRequest.PlayerDelta> entry :
                request.playerDeltas().entrySet()) {
            Long playerId = entry.getKey();
            MatchResultRequest.PlayerDelta deltas = entry.getValue();

            boolean isWinner = winners.contains(playerId);
            boolean isLoser = losers.contains(playerId);

            if (!isWinner && !isLoser) {
                continue;
            }

            int appliedDelta = isWinner ? deltas.winDelta() : -deltas.lossDelta();

            updatePlayerMMRInternal(playerId, queue, appliedDelta, isWinner, results, finalDeltas);
        }

        SaveMatchMMRGainsRequest gRpcRequest =
                SaveMatchMMRGainsRequest.newBuilder()
                        .setGameId(request.gameId())
                        .putAllPlayerMMRGains(finalDeltas)
                        .build();

        matchHistoryService
                .saveMatchMMRGains(gRpcRequest)
                .subscribe()
                .with(
                        response -> {
                            if (!response.getSuccess()) {
                            }
                        },
                        failure -> {
                        });

        return results;
    }

    private void updatePlayerMMRInternal(
            Long playerId,
            QueueEntity queue,
            int delta,
            boolean isWin,
            Map<Long, PlayerRankingDTO> results,
            Map<Long, Integer> playerDeltas) {
        Player player =
                playerRepository
                        .findByIdOptional(playerId)
                        .orElseThrow(() -> new PlayerNotFoundException("Player " + playerId + " not found"));

        PlayerRanking ranking =
                playerRankingRepository
                        .findByPlayerAndQueue(player, queue)
                        .orElseThrow(
                                () ->
                                        new MMRAlreadyExistsException(
                                                "Ranking not found for player " + playerId + " in this queue"));

        ranking.setMmr(ranking.getMmr() + delta);
        if (isWin) {
            ranking.setWins(ranking.getWins() + 1);
        } else {
            ranking.setLosses(ranking.getLosses() + 1);
        }

        playerRankingRepository.persist(ranking);
        results.put(playerId, playerRankingMapper.toDTO(ranking));
        playerDeltas.put(playerId, delta);
    }

    @Override
    public PaginatedResponseDTO<PlayerRankingDTO> getQueueLeaderboard(
            int page, int size, Optional<Long> queueId, Optional<Region> region) {
        PanacheQuery<PlayerRanking> query;
        StringBuilder queryStr = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        if (region.isPresent()) {
            queryStr.append(
                    "select pr from PlayerRanking pr join pr.player p join p.riotAccounts ra where ra.isPrimary = true and ra.region = :region");
            params.put("region", region.get());
            if (queueId.isPresent()) {
                queryStr.append(" and pr.queue.id = :queueId");
                params.put("queueId", queueId.get());
            }
        } else if (queueId.isPresent()) {
            queryStr.append("queue.id = :queueId");
            params.put("queueId", queueId.get());
        }

        if (queryStr.length() > 0) {
            query =
                    playerRankingRepository.find(queryStr.toString(), Sort.by("mmr").descending(), params);
        } else {
            query = playerRankingRepository.findAll(Sort.by("mmr").descending());
        }

        return new PaginatedResponseDTO<>(
                query.page(page, size).list().stream().map(playerRankingMapper::toDTO).toList(),
                page,
                query.pageCount(),
                query.count());
    }

    @Override
    @Transactional
    public PlayerRankingDTO populatePlayerMMR(
            Long playerId, CreatePlayerRequest createPlayerRequest) {
        Player player =
                playerRepository
                        .findByIdOptional(playerId)
                        .orElseThrow(() -> new PlayerNotFoundException("Player not found"));

        if (player.getPrimaryAccount() == null) {
            throw new LeagueAccountNotLinkedException(
                    "Player must link a League of Legends account first");
        }

        Long queueId =
                createPlayerRequest
                        .queueId()
                        .orElseThrow(() -> new QueueNotFoundException("Queue ID is required for population"));

        QueueEntity queue =
                queueRepository
                        .findByIdOptional(queueId)
                        .orElseThrow(() -> new QueueNotFoundException("Queue not found"));

        if (playerRankingRepository.findByPlayerAndQueue(player, queue).isPresent()) {
            throw new MMRAlreadyExistsException("MMR already exists for this player in this queue");
        }

        int initialMmr =
                switch (queue.getMmrRuleType()) {
                    case SOLOQ_RANK -> player.getSoloqMMR();
                    case FLEX_RANK -> player.getFlexMMR();
                    case NONE -> queue.getInitialMMR();
                };

        PlayerRanking ranking = new PlayerRanking();
        ranking.setPublicId(UUID.randomUUID());
        ranking.setPlayer(player);
        ranking.setQueue(queue);
        ranking.setMmr(initialMmr);

        playerRankingRepository.persist(ranking);
        return playerRankingMapper.toDTO(ranking);
    }
}
