package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.request.MatchFiltersDTO;
import fc.ul.scrimfinder.dto.request.SortParamDTO;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.exception.*;
import java.util.List;
import java.util.Map;

public interface MatchHistoryService {
    MatchDTO getMatchById(String riotMatchId)
            throws MatchNotFoundException,
                    InvalidExternalJsonFormatException,
                    ExternalServiceUnavailableException;

    PaginatedResponseDTO<MatchDTO> getMatches(
            int page, int size, MatchFiltersDTO filterParams, List<SortParamDTO> sortParamDTOS)
            throws InvalidIntervalException, InvalidTeamsException;

    MatchDTO addMatchById(String riotMatchId, Map<String, Integer> mmrDeltas)
            throws MatchAlreadyExistsException,
                    MatchNotFoundException,
                    NotEnoughMMRDeltasException,
                    PlayerNotFoundException;

    MatchDTO deleteMatchById(String riotMatchId) throws MatchNotFoundException;
}
