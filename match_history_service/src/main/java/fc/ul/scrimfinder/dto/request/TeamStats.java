package fc.ul.scrimfinder.dto.request;

import fc.ul.scrimfinder.util.TeamColor;
import jakarta.ws.rs.QueryParam;

public record TeamStats(
        @QueryParam("color") TeamColor color,
        @QueryParam("teamKills") Integer teamKills,
        @QueryParam("teamDeaths") Integer teamDeaths,
        @QueryParam("teamAssists") Integer teamAssists,
        @QueryParam("teamHealing") Integer teamHealing
) {
    public static TeamStats valueOf(String color) {
        return new TeamStats(null, null, null, null, null);
    }
}
