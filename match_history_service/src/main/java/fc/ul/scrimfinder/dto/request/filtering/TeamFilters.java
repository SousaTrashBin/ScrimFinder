package fc.ul.scrimfinder.dto.request.filtering;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fc.ul.scrimfinder.util.TeamSide;
import fc.ul.scrimfinder.util.interval.IntegerInterval;
import jakarta.ws.rs.QueryParam;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class TeamFilters {
    @QueryParam("side")
    private TeamSide side;

    @QueryParam("teamKills")
    private IntegerInterval teamKills;

    @QueryParam("teamDeaths")
    private IntegerInterval teamDeaths;

    @QueryParam("teamAssists")
    private IntegerInterval teamAssists;

    @QueryParam("teamHealing")
    private IntegerInterval teamHealing;

    public static TeamFilters valueOf(String value) {
        if (value == null || value.isBlank()) return null;

        ObjectMapper mapper = new ObjectMapper();
        JsonNode teamStats;
        try {
            teamStats = mapper.readTree(value);
        } catch (Exception x) {
            throw new IllegalArgumentException("Invalid team object format: " + value);
        }

        TeamSide side =
                fromJsonToField(teamStats, "side", node -> TeamSide.fromTeamSideName(node.asText()));
        IntegerInterval teamKills = fromJsonToField(teamStats, "teamKills", TeamFilters::getMinMaxInt);
        IntegerInterval teamDeaths =
                fromJsonToField(teamStats, "teamDeaths", TeamFilters::getMinMaxInt);
        IntegerInterval teamAssists =
                fromJsonToField(teamStats, "teamAssists", TeamFilters::getMinMaxInt);
        IntegerInterval teamHealing =
                fromJsonToField(teamStats, "teamHealing", TeamFilters::getMinMaxInt);

        return new TeamFilters(side, teamKills, teamDeaths, teamAssists, teamHealing);
    }

    private static <T> T fromJsonToField(JsonNode json, String field, Function<JsonNode, T> toField) {
        JsonNode targetNode = json.findValue(field);
        return targetNode == null ? null : toField.apply(targetNode);
    }

    private static IntegerInterval getMinMaxInt(JsonNode json) {
        return new IntegerInterval(
                fromJsonToField(json, "min", JsonNode::asInt),
                fromJsonToField(json, "max", JsonNode::asInt));
    }
}
