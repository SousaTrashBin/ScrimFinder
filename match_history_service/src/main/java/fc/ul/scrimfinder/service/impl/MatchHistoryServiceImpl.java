package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.dto.request.MatchFiltersDTO;
import fc.ul.scrimfinder.dto.request.MatchStatsDTO;
import fc.ul.scrimfinder.dto.request.SortParamDTO;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
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
    public MatchDTO getMatchById(Long matchId) {
        // TODO
        return null;
    }

    @Override
    public PaginatedResponseDTO<MatchDTO> getMatches(int page, int size, MatchFiltersDTO filterParams, List<SortParamDTO> sortParamDTOS) {
        // TODO
        return null;
    }

    @Override
    public MatchDTO addMatchById(String riotMatchId) {
        // TODO
        return null;
    }

    @Override
    public MatchDTO addMatch(MatchStatsDTO matchStatsDto) {
        // TODO
        return null;
    }

    @Override
    public MatchDTO deleteMatchById(Long matchId) throws MatchNotFoundException, ExternalServiceUnavailableException {
        // TODO
        return null;
    }
}
