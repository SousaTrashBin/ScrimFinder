package fc.ul.scrimfinder.dto.response.match;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fc.ul.scrimfinder.util.TeamSide;

public record TeamStats(
        TeamSide side,
        Integer teamKills,
        Integer teamDeaths,
        Integer teamAssists,
        Integer teamHealing
) {
    public static TeamStats valueOf(String value) {
        if (value == null || value.isBlank()) return null;

        ObjectMapper mapper = new ObjectMapper();
        JsonNode teamStats;
        try {
            teamStats = mapper.readTree(value);
        } catch (Exception x) {
            throw new IllegalArgumentException("Invalid team object format: " + value);
        }

        TeamSide teamSide = fromJsonToTeamSide(teamStats);
        Integer teamKills = fromJsonToTeamKills(teamStats);
        Integer teamDeaths = fromJsonToTeamDeaths(teamStats);
        Integer teamAssists = fromJsonToTeamAssists(teamStats);
        Integer teamHealing = fromJsonToTeamHealing(teamStats);

        return new TeamStats(
                teamSide,
                teamKills,
                teamDeaths,
                teamAssists,
                teamHealing
        );
    }

    private static TeamSide fromJsonToTeamSide(JsonNode json) {
        JsonNode teamSideNode = json.findValue("teamSide");
        if (teamSideNode == null) return null;
        String teamSideName = teamSideNode.asText();
        return TeamSide.fromTeamSideName(teamSideName);
    }

    private static Integer fromJsonToTeamKills(JsonNode json) {
        JsonNode teamKillsNode = json.findValue("teamKills");
        return teamKillsNode == null ? null : teamKillsNode.asInt();
    }

    private static Integer fromJsonToTeamDeaths(JsonNode json) {
        JsonNode teamDeathsNode = json.findValue("teamDeaths");
        return teamDeathsNode == null ? null : teamDeathsNode.asInt();
    }

    private static Integer fromJsonToTeamAssists(JsonNode json) {
        JsonNode teamAssistsNode = json.findValue("teamAssists");
        return teamAssistsNode == null ? null : teamAssistsNode.asInt();
    }

    private static Integer fromJsonToTeamHealing(JsonNode json) {
        JsonNode teamHealingNode = json.findValue("teamHealing");
        return teamHealingNode == null ? null : teamHealingNode.asInt();
    }
}
