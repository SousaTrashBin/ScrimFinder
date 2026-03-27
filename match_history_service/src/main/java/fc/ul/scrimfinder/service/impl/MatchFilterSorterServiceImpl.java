package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.domain.Match;
import fc.ul.scrimfinder.dto.request.filtering.MatchFilters;
import fc.ul.scrimfinder.dto.request.sorting.SortParams;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.mapper.MatchMapper;
import fc.ul.scrimfinder.repository.MatchHistoryRepository;
import fc.ul.scrimfinder.service.MatchFilterSorterService;
import fc.ul.scrimfinder.util.FilterValidator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
@Transactional
public class MatchFilterSorterServiceImpl implements MatchFilterSorterService {

    @Inject MatchHistoryRepository matchHistoryRepository;

    @Inject MatchMapper matchMapper;

    @Override
    public PaginatedResponseDTO<MatchDTO> filterSortMatches(
            int page, int size, MatchFilters filterParams, List<SortParams> sortParams) {
        FilterValidator.validateInput(page, size, filterParams);
        PaginatedResponseDTO<Match> matches =
                matchHistoryRepository.search(page, size, filterParams, sortParams);
        return new PaginatedResponseDTO<>(
                matches.data().stream().map(matchMapper::matchToDto).toList(),
                matches.currentPage(),
                matches.totalPages(),
                matches.totalElements());
    }
}
