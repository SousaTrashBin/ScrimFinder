package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.request.MatchAddDto;
import fc.ul.scrimfinder.dto.request.MatchStats;
import fc.ul.scrimfinder.dto.response.MatchFullDto;
import fc.ul.scrimfinder.dto.response.MatchSimplifiedDto;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDto;

public interface MatchHistoryService {
    MatchFullDto getMatchById(Long matchId);

    PaginatedResponseDto<MatchSimplifiedDto> getMatches(int page, int size, MatchStats params);

    MatchSimplifiedDto addMatch(MatchAddDto matchAddDto);
}
