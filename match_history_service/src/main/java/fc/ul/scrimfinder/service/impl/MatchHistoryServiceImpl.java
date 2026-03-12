package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.dto.request.MatchAddDto;
import fc.ul.scrimfinder.dto.request.MatchStats;
import fc.ul.scrimfinder.dto.request.SortParam;
import fc.ul.scrimfinder.dto.response.MatchDto;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDto;
import fc.ul.scrimfinder.exception.ExternalServiceUnavailableException;
import fc.ul.scrimfinder.exception.MatchNotFoundException;
import fc.ul.scrimfinder.mapper.MatchMapper;
import fc.ul.scrimfinder.repository.MatchHistoryRepository;
import fc.ul.scrimfinder.service.MatchHistoryService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class MatchHistoryServiceImpl implements MatchHistoryService {
    @Inject
    MatchHistoryRepository matchHistoryRepository;

    @Inject
    MatchMapper matchMapper;

    @Override
    public MatchDto getMatchById(Long matchId) {
        // TODO
        return null;
    }

    @Override
    public PaginatedResponseDto<MatchDto> getMatches(int page, int size, MatchStats filterParams, List<SortParam> sortParams) {
        // TODO
        return null;
    }

    @Override
    public MatchDto addMatchById(String riotMatchId) {
        // TODO
        return null;
    }

    @Override
    public MatchDto addMatch(MatchAddDto matchAddDto) {
        // TODO
        return null;
    }

    @Override
    public MatchDto delMatchById(Long matchId) throws MatchNotFoundException, ExternalServiceUnavailableException {
        // TODO
        return null;
    }
}
