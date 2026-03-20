package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.dto.response.match.MatchStatsDTO;
import fc.ul.scrimfinder.service.MatchFillingService;
import fc.ul.scrimfinder.service.RiotAdapterService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MatchFillingServiceImpl implements MatchFillingService {

    @Inject RiotAdapterService riotAdapterService;

    @Override
    public MatchStatsDTO getFilledMatch(String matchId) {
        return riotAdapterService.getMatchData(matchId);
    }

    @Override
    public String getRawMatchData(String matchId) {
        return riotAdapterService.getRawMatchData(matchId);
    }
}
