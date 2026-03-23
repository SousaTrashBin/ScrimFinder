package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.request.filtering.MatchFilters;
import fc.ul.scrimfinder.dto.request.sorting.SortParams;
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
            int page, int size, MatchFilters filterParams, List<SortParams> sortParams)
            throws InvalidIntervalException, InvalidTeamsException;

    MatchDTO addMatchById(String riotMatchId, Map<String, Integer> mmrDeltas)
            throws MatchAlreadyExistsException,
                    MatchNotFoundException,
                    NotEnoughMMRDeltasException,
                    PlayerNotFoundException;

    MatchDTO deleteMatchById(String riotMatchId) throws MatchNotFoundException;
}
