package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.request.filtering.MatchFilters;
import fc.ul.scrimfinder.dto.request.sorting.SortParams;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.exception.InvalidIntervalException;
import fc.ul.scrimfinder.exception.InvalidTeamsException;
import java.util.List;

public interface MatchFilterSorterService {
    PaginatedResponseDTO<MatchDTO> filterSortMatches(
            int page, int size, MatchFilters filterParams, List<SortParams> sortParams)
            throws InvalidIntervalException, InvalidTeamsException;
}
