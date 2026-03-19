package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.request.MatchAddDTO;
import fc.ul.scrimfinder.dto.request.MatchStats;
import fc.ul.scrimfinder.dto.request.SortParam;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.exception.*;
import java.util.List;
import java.util.Map;

public interface MatchHistoryService {
    MatchDTO getMatchById(Long matchId)
            throws MatchNotFoundException, ExternalServiceUnavailableException;

    PaginatedResponseDTO<MatchDTO> getMatches(
            int page, int size, MatchStats filterParams, List<SortParam> sortParams)
            throws PlayerNotFoundException,
                    ChampionNotFoundException,
                    RankNotFoundException,
                    InvalidIntervalException,
                    InvalidRoleException,
                    InvalidTeamsException;

    MatchDTO addMatchById(String riotMatchId, Map<Long, Integer> playerMMRGains)
            throws MatchAlreadyExistsException,
                    MatchNotFoundException,
                    InvalidIntervalException,
                    InvalidTeamsException;

    MatchDTO addMatch(MatchAddDTO matchAddDto)
            throws MatchAlreadyExistsException, InvalidIntervalException, InvalidTeamsException;

    MatchDTO deleteMatchById(Long matchId)
            throws MatchNotFoundException, ExternalServiceUnavailableException;
}
