package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.domain.Match;
import fc.ul.scrimfinder.dto.request.MatchFiltersDTO;
import fc.ul.scrimfinder.dto.request.SortParamDTO;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.exception.ExternalServiceUnavailableException;
import fc.ul.scrimfinder.exception.MatchNotFoundException;
import fc.ul.scrimfinder.mapper.MatchMapper;
import fc.ul.scrimfinder.repository.MatchHistoryRepository;
import fc.ul.scrimfinder.service.AnalysisAdapterService;
import fc.ul.scrimfinder.service.DetailFillingAdapterService;
import fc.ul.scrimfinder.service.MatchHistoryService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
@Transactional
public class MatchHistoryServiceImpl implements MatchHistoryService {
    @Inject DetailFillingAdapterService detailFillingAdapterService;

    @Inject AnalysisAdapterService analysisAdapterService;

    @Inject MatchHistoryRepository matchHistoryRepository;

    @Inject MatchMapper matchMapper;

    @Override
    public MatchDTO getMatchById(String riotMatchId) {
        Optional<Match> maybeMatch = matchHistoryRepository.findByRiotMatchId(riotMatchId);
        if (maybeMatch.isPresent()) {
            return matchMapper.matchToDto(maybeMatch.get());
        }
        return addMatchById(riotMatchId, Map.of());
    }

    @Override
    public PaginatedResponseDTO<MatchDTO> getMatches(
            int page, int size, MatchFiltersDTO filterParams, List<SortParamDTO> sortParamDTOS) {
        // TODO
        return null;
    }

    @Override
    public MatchDTO addMatchById(String riotMatchId, Map<Long, Integer> playerMMRGains) {
        // TODO
        return detailFillingAdapterService.getMatch(riotMatchId);
    }

    @Override
    public MatchDTO deleteMatchById(String riotMatchId)
            throws MatchNotFoundException, ExternalServiceUnavailableException {
        // TODO
        return null;
    }
}
