package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.request.MatchFiltersDTO;
import fc.ul.scrimfinder.dto.request.SortParamDTO;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.exception.*;
import fc.ul.scrimfinder.util.PlayerMMRDelta;

import java.util.List;

public interface MatchHistoryService {
    MatchDTO getMatchById(Long matchId) throws
            MatchNotFoundException,
            ExternalServiceUnavailableException;

    PaginatedResponseDTO<MatchDTO> getMatches(int page, int size, MatchFiltersDTO filterParams, List<SortParamDTO> sortParamDTOS) throws
            PlayerNotFoundException,
            ChampionNotFoundException,
            RankNotFoundException,
            InvalidIntervalException,
            InvalidRoleException,
            InvalidTeamsException;

    MatchDTO addMatchById(String riotMatchId, String queueId, List<PlayerMMRDelta> mmrDeltas) throws
            MatchAlreadyExistsException,
            MatchNotFoundException,
            InvalidIntervalException,
            InvalidTeamsException;

    MatchDTO deleteMatchById(Long matchId) throws
            MatchNotFoundException,
            ExternalServiceUnavailableException;
}
