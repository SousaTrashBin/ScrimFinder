package fc.ul.scrimfinder.dto.request;

import fc.ul.scrimfinder.util.TeamColor;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.QueryParam;

public record TeamStats(
        @QueryParam("color")
        TeamColor color,

        @QueryParam("teamKills")
        @Min(value = 0, message = "A team can only have more than or equal to 0 kills")
        Integer teamKills,

        @QueryParam("teamDeaths")
        @Min(value = 0, message = "A team can only have more than or equal to 0 deaths")
        Integer teamDeaths,

        @QueryParam("teamAssists")
        @Min(value = 0, message = "A team can only have more than or equal to 0 assists")
        Integer teamAssists,

        @QueryParam("teamHealing")
        @Min(value = 0, message = "A team can only have more than or equal to 0 healing")
        Integer teamHealing
) {
    public static TeamStats valueOf(String color) {
        return new TeamStats(null, null, null, null, null);
    }
}
