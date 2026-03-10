package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.dto.response.match.MatchDto;
import fc.ul.scrimfinder.service.MatchFillingService;
import fc.ul.scrimfinder.service.RiotAdapterService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MatchFillingServiceImpl implements MatchFillingService {

    @Inject
    RiotAdapterService riotAdapterService;

    @Override
    public MatchDto getMatchById(Long matchId) {
        return null;
    }
}
