package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.dto.request.MatchAddDTO;
import fc.ul.scrimfinder.dto.request.MatchStats;
import fc.ul.scrimfinder.dto.request.SortParam;
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
import java.util.Map;

@ApplicationScoped
public class MatchHistoryServiceImpl implements MatchHistoryService {
    @Inject MatchHistoryRepository matchHistoryRepository;

    @Inject MatchMapper matchMapper;

    @Override
    public MatchDTO getMatchById(Long matchId) {
        // TODO
        return null;
    }

    @Override
    public PaginatedResponseDTO<MatchDTO> getMatches(
            int page, int size, MatchStats filterParams, List<SortParam> sortParams) {
        // TODO
        return null;
    }

    @Override
    public MatchDTO addMatchById(String riotMatchId, Map<Long, Integer> playerMMRGains) {
        // TODO
        return null;
    }

    @Override
    public MatchDTO addMatch(MatchAddDTO matchAddDto) {
        // TODO
        return null;
    }

    @Override
    public MatchDTO deleteMatchById(Long matchId)
            throws MatchNotFoundException, ExternalServiceUnavailableException {
        // TODO
        return null;
    }
}
