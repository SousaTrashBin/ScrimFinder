package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.domain.Match;
import fc.ul.scrimfinder.domain.Player;
import fc.ul.scrimfinder.domain.PlayerMatchStats;
import fc.ul.scrimfinder.dto.request.filtering.MatchFilters;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.exception.*;
import fc.ul.scrimfinder.mapper.MatchMapper;
import fc.ul.scrimfinder.mapper.PlayerMapper;
import fc.ul.scrimfinder.mapper.PlayerMatchStatsMapper;
import fc.ul.scrimfinder.repository.MatchHistoryRepository;
import fc.ul.scrimfinder.repository.PlayerMatchStatsRepository;
import fc.ul.scrimfinder.repository.PlayerRepository;
import fc.ul.scrimfinder.repository.ReplicaMatchHistoryReadRepository;
import fc.ul.scrimfinder.service.DetailFillingAdapterService;
import fc.ul.scrimfinder.service.MatchFilterSorterService;
import fc.ul.scrimfinder.service.MatchHistoryService;
import fc.ul.scrimfinder.util.ColoredMessage;
import fc.ul.scrimfinder.util.LogColor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jboss.logging.Logger;

@ApplicationScoped
@Transactional
public class MatchHistoryServiceImpl implements MatchHistoryService {
    @Inject MatchFilterSorterService matchFilterSorterService;

    @Inject DetailFillingAdapterService detailFillingAdapterService;

    @Inject TrainingSyncOutboxService trainingSyncOutboxService;

    @Inject MatchHistoryRepository matchHistoryRepository;

    @Inject
    fc.ul.scrimfinder.repository.ReadOnlyMatchHistoryRepository readOnlyMatchHistoryRepository;

    @Inject PlayerRepository playerRepository;

    @Inject PlayerMatchStatsRepository playerMatchStatsRepository;

    @Inject MatchMapper matchMapper;

    @Inject PlayerMapper playerMapper;

    @Inject PlayerMatchStatsMapper playerMatchStatsMapper;
    @Inject ReplicaMatchHistoryReadRepository replicaReadRepository;
    @Inject TransactionSynchronizationRegistry txSyncRegistry;

    @Inject Logger logger;

    @Override
    public MatchDTO getMatchById(String riotMatchId) {
        logger.info(
                ColoredMessage.withColor(
                        "GET match by ID (from Read Replica): " + riotMatchId, LogColor.GREEN));
        if (isTransactionActive()) {
            return readOnlyMatchHistoryRepository
                    .findByRiotMatchId(riotMatchId)
                    .map(matchMapper::matchToDto)
                    .orElseThrow(() -> new MatchNotFoundException("Match does not exist in history"));
        }
        try {
            return replicaReadRepository
                    .findByRiotMatchId(riotMatchId)
                    .orElseThrow(() -> new MatchNotFoundException("Match does not exist in history"));
        } catch (MatchNotFoundException e) {
            logger.warn(
                    ColoredMessage.withColor(
                            "Match does not exist in history: " + riotMatchId, LogColor.YELLOW));
            throw e;
        } catch (Exception replicaFailure) {
            logger.warn(
                    ColoredMessage.withColor(
                            "Replica read failed for match "
                                    + riotMatchId
                                    + ", falling back to primary: "
                                    + replicaFailure.getMessage(),
                            LogColor.YELLOW));
            return readOnlyMatchHistoryRepository
                    .findByRiotMatchId(riotMatchId)
                    .map(matchMapper::matchToDto)
                    .orElseThrow(() -> new MatchNotFoundException("Match does not exist in history"));
        }
    }

    @Override
    public PaginatedResponseDTO<MatchDTO> getMatches(int page, int size, MatchFilters filterParams) {
        logger.info(
                ColoredMessage.withColor(
                        String.format(
                                "GET filtered matches (page=%d | size=%d) with filters: %s",
                                page, size, filterParams),
                        LogColor.GREEN));
        if (!isTransactionActive() && isEmptyFilter(filterParams)) {
            try {
                return replicaReadRepository.findAllPaged(page, size);
            } catch (Exception replicaFailure) {
                logger.warn(
                        ColoredMessage.withColor(
                                "Replica read failed for unfiltered matches, falling back to primary: "
                                        + replicaFailure.getMessage(),
                                LogColor.YELLOW));
            }
        }
        return matchFilterSorterService.filterSortMatches(page, size, filterParams);
    }

    private boolean isEmptyFilter(MatchFilters filters) {
        return filters == null
                || (filters.getQueueId() == null
                        && (filters.getChampions() == null || filters.getChampions().isEmpty())
                        && filters.getPatch() == null
                        && filters.getTime() == null
                        && (filters.getPlayers() == null || filters.getPlayers().isEmpty())
                        && (filters.getTeams() == null || filters.getTeams().isEmpty())
                        && (filters.getSortParams() == null || filters.getSortParams().isEmpty()));
    }

    private boolean isTransactionActive() {
        int status = txSyncRegistry.getTransactionStatus();
        return status == Status.STATUS_ACTIVE || status == Status.STATUS_MARKED_ROLLBACK;
    }

    @Override
    public MatchDTO addMatchById(
            String riotMatchId, UUID queueId, Map<String, Integer> playerMMRGains) {
        logger.info(
                ColoredMessage.withColor(
                        String.format(
                                "POST match to history (ID=%s | Queue ID=%s | Player MMR deltas=%s)",
                                riotMatchId, queueId.toString(), playerMMRGains),
                        LogColor.GREEN));
        Optional<Match> maybeMatch = matchHistoryRepository.findByRiotMatchId(riotMatchId);
        if (maybeMatch.isPresent()) {
            logger.warn(
                    ColoredMessage.withColor(
                            String.format("Match %s already exists in the history", riotMatchId),
                            LogColor.YELLOW));
            throw new MatchAlreadyExistsException(
                    "A match with this Riot ID already exists in the match history");
        }
        MatchDTO matchDTO = detailFillingAdapterService.getMatch(riotMatchId);
        matchDTO.setQueueId(queueId);
        Match match = matchMapper.dtoToMatchWithNoPlayerStats(matchDTO);

        if (playerMMRGains.size() != matchDTO.getPlayers().size()) {
            logger.error(
                    ColoredMessage.withColor(
                            String.format(
                                    "Only %d MMR deltas were provided for %d players in match %s",
                                    playerMMRGains.size(), matchDTO.getPlayers().size(), riotMatchId),
                            LogColor.RED));
            throw new NotEnoughMMRDeltasException(
                    String.format(
                            "This match has %d players but only %d MMR deltas were provided",
                            matchDTO.getPlayers().size(), playerMMRGains.size()));
        }

        matchDTO
                .getPlayers()
                .forEach(
                        playerStatsDTO -> {
                            String puuid = playerStatsDTO.getRiotId().getPuuid();
                            playerStatsDTO.setMmrDelta(playerMMRGains.getOrDefault(puuid, null));

                            if (playerStatsDTO.getMmrDelta() == null) {
                                logger.error(
                                        ColoredMessage.withColor(
                                                String.format("No MMR for player %s in match %s", puuid, riotMatchId),
                                                LogColor.RED));
                                throw new PlayerNotFoundException(
                                        "No player found in this match for the MMR delta provided. Puuid of the incorrect player: "
                                                + puuid);
                            }

                            PlayerMatchStats playerMatchStats =
                                    playerMatchStatsMapper.dtoToPlayerMatchStats(playerStatsDTO);

                            Player player = playerMapper.playerMatchStatsDTOToPlayer(playerStatsDTO);

                            Optional<Player> maybePlayer = playerRepository.findByPuuid(puuid);
                            if (maybePlayer.isPresent()) {
                                player = maybePlayer.get();
                            }
                            player.addPlayerMatchStat(playerMatchStats);
                            playerRepository.persist(player);

                            match.addPlayerMatchStat(playerMatchStats);
                        });

        matchHistoryRepository.persist(match); // cascade to all player stats as well
        trainingSyncOutboxService.enqueue(riotMatchId);

        if (!trainingSyncOutboxService.processSinglePending(riotMatchId)) {
            logger.warn(
                    ColoredMessage.withColor(
                            String.format(
                                    "Match %s saved locally, training sync queued for retry via outbox", riotMatchId),
                            LogColor.YELLOW));
        } else {
            logger.info(
                    ColoredMessage.withColor(
                            String.format("Match %s sent to the training service", riotMatchId), LogColor.GREEN));
        }
        return matchDTO;
    }

    @Override
    public MatchDTO deleteMatchById(String riotMatchId) {
        logger.info(
                ColoredMessage.withColor(
                        String.format("DELETE match with ID: %s", riotMatchId), LogColor.GREEN));
        MatchDTO matchDTO = getMatchById(riotMatchId);
        if (!matchHistoryRepository.deleteByRiotMatchId(riotMatchId)) {
            logger.error(
                    ColoredMessage.withColor("Failed to delete match with ID: " + riotMatchId, LogColor.RED));
            throw new RuntimeException("Failed to delete match from history");
        }
        return matchDTO;
    }
}
