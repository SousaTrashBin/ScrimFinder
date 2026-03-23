package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.dto.request.filtering.MatchFilters;
import fc.ul.scrimfinder.dto.request.sorting.SortParams;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.service.MatchFilterSorterService;
import fc.ul.scrimfinder.util.FilterValidator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import org.jboss.logging.Logger;

@ApplicationScoped
public class MatchFilterSorterServiceImpl implements MatchFilterSorterService {

    @Inject Logger logger;

    @Override
    public PaginatedResponseDTO<MatchDTO> filterSortMatches(
            int page, int size, MatchFilters filterParams, List<SortParams> sortParams) {
        FilterValidator.validateInput(page, size, filterParams);
        return null;
    }
}
