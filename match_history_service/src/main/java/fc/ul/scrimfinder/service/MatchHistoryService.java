package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.request.MatchAddDto;
import fc.ul.scrimfinder.dto.request.MatchStats;
import fc.ul.scrimfinder.dto.request.SortParam;
import fc.ul.scrimfinder.dto.response.matchfull.MatchFullDto;
import fc.ul.scrimfinder.dto.response.MatchSimplifiedDto;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDto;
import fc.ul.scrimfinder.exception.*;

import java.util.List;

public interface MatchHistoryService {
    MatchFullDto getMatchById(Long matchId) throws
            MatchNotFoundException,
            ExternalServiceUnavailableException;

    PaginatedResponseDto<MatchSimplifiedDto> getMatches(int page, int size, MatchStats filterParams, List<SortParam> sortParams) throws
            PlayerNotFoundException,
            ChampionNotFoundException,
            RankNotFoundException,
            InvalidIntervalException,
            InvalidRoleException,
            InvalidTeamsException;

    MatchSimplifiedDto addMatch(MatchAddDto matchAddDto) throws
            MatchAlreadyExistsException,
            InvalidIntervalException,
            InvalidTeamsException;
}
