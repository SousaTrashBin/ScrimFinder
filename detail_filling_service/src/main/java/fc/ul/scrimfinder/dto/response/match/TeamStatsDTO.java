package fc.ul.scrimfinder.dto.response.match;

import fc.ul.scrimfinder.util.TeamSide;

public record TeamStatsDTO(
        TeamSide side,
        Integer teamKills,
        Integer teamDeaths,
        Integer teamAssists,
        Integer teamHealing
) {
}