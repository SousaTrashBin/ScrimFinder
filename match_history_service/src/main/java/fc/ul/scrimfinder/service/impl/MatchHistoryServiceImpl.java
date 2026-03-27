package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.domain.Match;
import fc.ul.scrimfinder.domain.Player;
import fc.ul.scrimfinder.domain.PlayerMatchStats;
import fc.ul.scrimfinder.dto.request.filtering.MatchFilters;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.exception.*;
import fc.ul.scrimfinder.mapper.MatchMapper;
import fc.ul.scrimfinder.mapper.PlayerMatchStatsMapper;
import fc.ul.scrimfinder.repository.MatchHistoryRepository;
import fc.ul.scrimfinder.repository.PlayerMatchStatsRepository;
import fc.ul.scrimfinder.repository.PlayerRepository;
import fc.ul.scrimfinder.service.AnalysisAdapterService;
import fc.ul.scrimfinder.service.DetailFillingAdapterService;
import fc.ul.scrimfinder.service.MatchFilterSorterService;
import fc.ul.scrimfinder.service.MatchHistoryService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.Optional;
import org.jboss.logging.Logger;

@ApplicationScoped
@Transactional
public class MatchHistoryServiceImpl implements MatchHistoryService {
    @Inject MatchFilterSorterService matchFilterSorterService;

    @Inject DetailFillingAdapterService detailFillingAdapterService;

    @Inject AnalysisAdapterService analysisAdapterService;

    @Inject MatchHistoryRepository matchHistoryRepository;

    @Inject PlayerRepository playerRepository;

    @Inject PlayerMatchStatsRepository playerMatchStatsRepository;

    @Inject MatchMapper matchMapper;

    @Inject PlayerMatchStatsMapper playerMatchStatsMapper;

    @Inject Logger logger;

    @Override
    public MatchDTO getMatchById(String riotMatchId) {
        return matchHistoryRepository
                .findByRiotMatchId(riotMatchId)
                .map(matchMapper::matchToDto)
                .orElseThrow(() -> new MatchNotFoundException("Match does not exist in history"));
    }

    @Override
    public PaginatedResponseDTO<MatchDTO> getMatches(int page, int size, MatchFilters filterParams) {
        return matchFilterSorterService.filterSortMatches(page, size, filterParams);
    }

    @Override
    public MatchDTO addMatchById(String riotMatchId, Map<String, Integer> playerMMRGains) {
        Optional<Match> maybeMatch = matchHistoryRepository.findByRiotMatchId(riotMatchId);
        if (maybeMatch.isPresent()) {
            throw new MatchAlreadyExistsException(
                    "A match with this Riot ID already exists in the match history");
        }
        MatchDTO matchDTO = detailFillingAdapterService.getMatch(riotMatchId);
        Match match = matchMapper.dtoToMatchWithNoPlayerStats(matchDTO);

        if (playerMMRGains.size() != matchDTO.players().size()) {
            throw new NotEnoughMMRDeltasException(
                    String.format(
                            "This match has %d players but only %d MMR deltas were provided",
                            matchDTO.players().size(), playerMMRGains.size()));
        }

        matchDTO
                .players()
                .forEach(
                        playerStatsDTO -> {
                            String puuid = playerStatsDTO.getRiotId().getPuuid();
                            String name = playerStatsDTO.getRiotId().getPlayerName();
                            String tag = playerStatsDTO.getRiotId().getPlayerTag();
                            playerStatsDTO.setMmrDelta(playerMMRGains.getOrDefault(puuid, null));

                            if (playerStatsDTO.getMmrDelta() == null) {
                                throw new PlayerNotFoundException(
                                        "No player found in this match for the MMR delta provided. Puuid of the incorrect player: "
                                                + puuid);
                            }

                            PlayerMatchStats playerMatchStats =
                                    playerMatchStatsMapper.dtoToPlayerMatchStats(playerStatsDTO);

                            Player player = new Player();
                            player.setPuuid(puuid);
                            player.setName(name);
                            player.setTag(tag);

                            Optional<Player> maybePlayer = playerRepository.findByPuuid(puuid);
                            if (maybePlayer.isPresent()) {
                                player = maybePlayer.get();
                            }
                            player.addPlayerMatchStat(playerMatchStats);
                            playerRepository.persist(player);

                            match.addPlayerMatchStat(playerMatchStats);
                        });

        matchHistoryRepository.persist(match); // cascade to all player stats as well

        if (!analysisAdapterService.sendMatchForAnalysis(matchDTO)) {
            logger.warn("Failed to send match to the analysis service. Riot match ID: " + riotMatchId);
        }
        return matchDTO;
    }

    @Override
    public MatchDTO deleteMatchById(String riotMatchId) {
        MatchDTO matchDTO = getMatchById(riotMatchId);
        if (!matchHistoryRepository.deleteByRiotMatchId(riotMatchId)) {
            throw new RuntimeException("Failed to delete match from history");
        }
        return matchDTO;
    }
}
