package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.request.MatchAddDto;
import fc.ul.scrimfinder.dto.request.MatchStats;
import fc.ul.scrimfinder.dto.request.SortParam;
import fc.ul.scrimfinder.dto.response.MatchDto;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDto;
import fc.ul.scrimfinder.exception.*;

import java.util.List;

public interface MatchHistoryService {
    MatchDto getMatchById(Long matchId) throws
            MatchNotFoundException,
            ExternalServiceUnavailableException;

    PaginatedResponseDto<MatchDto> getMatches(int page, int size, MatchStats filterParams, List<SortParam> sortParams) throws
            PlayerNotFoundException,
            ChampionNotFoundException,
            RankNotFoundException,
            InvalidIntervalException,
            InvalidRoleException,
            InvalidTeamsException;

    MatchDto addMatchById(String riotMatchId) throws
            MatchAlreadyExistsException,
            MatchNotFoundException,
            InvalidIntervalException,
            InvalidTeamsException;

    MatchDto addMatch(MatchAddDto matchAddDto) throws
            MatchAlreadyExistsException,
            InvalidIntervalException,
            InvalidTeamsException;

    MatchDto delMatchById(Long matchId) throws
            MatchNotFoundException,
            ExternalServiceUnavailableException;
}
