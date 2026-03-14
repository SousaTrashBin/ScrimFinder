package fc.ul.scrimfinder.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fc.ul.scrimfinder.dto.response.match.MatchDTO;
import fc.ul.scrimfinder.dto.response.match.MatchStatsDTO;
import fc.ul.scrimfinder.dto.response.match.PlayerStats;
import fc.ul.scrimfinder.dto.response.match.TeamStats;
import fc.ul.scrimfinder.service.MatchFillingService;
import fc.ul.scrimfinder.service.RiotAdapterService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class MatchFillingServiceImpl implements MatchFillingService {

    @Inject
    RiotAdapterService riotAdapterService;

    @Override
    public MatchDTO getFilledMatch(Long matchId) {
        return null;
    }

    @Override
    public String getRawMatchData(Long matchId) {
        return "";
    }
}

