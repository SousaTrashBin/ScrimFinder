package fc.ul.scrimfinder.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fc.ul.scrimfinder.exception.InvalidTeamsException;
import fc.ul.scrimfinder.util.Champion;
import fc.ul.scrimfinder.util.RiotId;
import fc.ul.scrimfinder.util.Role;
import fc.ul.scrimfinder.util.TeamSide;
import jakarta.enterprise.inject.Vetoed;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Vetoed
public class PlayerStatsDTO {
    private RiotId riotId;
    private Integer kills;
    private Integer deaths;
    private Integer assists;
    private Integer healing;
    private Integer damageToPlayers;
    private Integer wards;
    private Integer gold;
    private Role role;
    private Champion champion;
    private Double csPerMinute;
    private Integer killedMinions;
    private Integer tripleKills;
    private Integer quadKills;
    private Integer pentaKills;
    private TeamSide side;
    private Boolean won;
    private Integer mmrDelta;

    public static PlayerStatsDTO valueOf(String value) {
        if (value == null || value.isBlank()) return null;

        ObjectMapper mapper = new ObjectMapper();
        JsonNode playerStats;
        try {
            playerStats = mapper.readTree(value);
        } catch (Exception x) {
            throw new IllegalArgumentException("Invalid player object format: " + value);
        }

        RiotId riotId = fromJsonToRiotId(playerStats);
        Integer kills = fromJsonToKills(playerStats);
        Integer deaths = fromJsonToDeaths(playerStats);
        Integer assists = fromJsonToAssists(playerStats);
        Integer healing = fromJsonToHealing(playerStats);
        Integer damageToPlayers = fromJsonToDamageToPlayers(playerStats);
        Integer wards = fromJsonToWards(playerStats);
        Integer gold = fromJsonToGold(playerStats);
        Role role = fromJsonToRole(playerStats);
        Champion champion = fromJsonToChampion(playerStats);
        Double csPerMinute = fromJsonToCsPerMinute(playerStats);
        Integer killedMinions = fromJsonToKilledMinions(playerStats);
        Integer tripleKills = fromJsonToTripleKills(playerStats);
        Integer quadKills = fromJsonToQuadKills(playerStats);
        Integer pentaKills = fromJsonToPentaKills(playerStats);
        TeamSide teamSide = fromJsonToTeamSide(playerStats);
        Boolean won = fromJsonToWon(playerStats);
        Integer mmrDelta = fromJsonToMmrDelta(playerStats);

        return new PlayerStatsDTO(
                riotId,
                kills,
                deaths,
                assists,
                healing,
                damageToPlayers,
                wards,
                gold,
                role,
                champion,
                csPerMinute,
                killedMinions,
                tripleKills,
                quadKills,
                pentaKills,
                teamSide,
                won,
                mmrDelta);
    }

    private static RiotId fromJsonToRiotId(JsonNode json) {
        JsonNode riotIdNode = json.get("riotId");
        if (riotIdNode == null) {
            throw new IllegalArgumentException("Riot ID is required: " + json);
        }

        JsonNode puuidNode = riotIdNode.findValue("puuid");
        if (puuidNode == null) {
            throw new IllegalArgumentException("Puuid is required: " + json);
        }
        String puuid = puuidNode.asText();

        JsonNode playerNameNode = riotIdNode.findValue("playerName");
        if (playerNameNode == null) {
            throw new IllegalArgumentException("Player name is required: " + json);
        }
        String playerName = playerNameNode.asText();

        JsonNode playerTagNode = riotIdNode.findValue("playerTag");
        if (playerTagNode == null) {
            throw new IllegalArgumentException("Player tag is required: " + json);
        }
        String playerTag = playerTagNode.asText();

        JsonNode playerIconNode = riotIdNode.findValue("playerIcon");
        Integer playerIcon = playerIconNode == null ? 0 : playerIconNode.asInt();

        return new RiotId(puuid, playerName, playerTag, playerIcon);
    }

    private static Integer fromJsonToKills(JsonNode json) {
        JsonNode killsNode = json.findValue("kills");
        return killsNode == null ? null : killsNode.asInt();
    }

    private static Integer fromJsonToDeaths(JsonNode json) {
        JsonNode deathsNode = json.findValue("deaths");
        return deathsNode == null ? null : deathsNode.asInt();
    }

    private static Integer fromJsonToAssists(JsonNode json) {
        JsonNode assistsNode = json.findValue("assists");
        return assistsNode == null ? null : assistsNode.asInt();
    }

    private static Integer fromJsonToHealing(JsonNode json) {
        JsonNode healingNode = json.findValue("healing");
        return healingNode == null ? null : healingNode.asInt();
    }

    private static Integer fromJsonToDamageToPlayers(JsonNode json) {
        JsonNode damageToPlayersNode = json.findValue("damageToPlayers");
        return damageToPlayersNode == null ? null : damageToPlayersNode.asInt();
    }

    private static Integer fromJsonToWards(JsonNode json) {
        JsonNode wardsNode = json.findValue("wards");
        return wardsNode == null ? null : wardsNode.asInt();
    }

    private static Integer fromJsonToGold(JsonNode json) {
        JsonNode goldNode = json.findValue("gold");
        return goldNode == null ? null : goldNode.asInt();
    }

    private static Role fromJsonToRole(JsonNode json) {
        JsonNode roleNode = json.findValue("role");
        if (roleNode == null) return null;
        String roleName = roleNode.asText();
        return Role.fromRoleName(roleName);
    }

    private static Champion fromJsonToChampion(JsonNode json) {
        JsonNode championNode = json.findValue("champion");
        return championNode == null ? null : Champion.fromChampionName(championNode.asText());
    }

    private static Integer fromJsonToKilledMinions(JsonNode json) {
        JsonNode killedMinionsNode = json.findValue("killedMinions");
        return killedMinionsNode == null ? null : killedMinionsNode.asInt();
    }

    private static Double fromJsonToCsPerMinute(JsonNode json) {
        JsonNode csPerMinuteNode = json.findValue("csPerMinute");
        return csPerMinuteNode == null ? null : csPerMinuteNode.asDouble();
    }

    private static Integer fromJsonToTripleKills(JsonNode json) {
        JsonNode tripleKillsNode = json.findValue("tripleKills");
        return tripleKillsNode == null ? null : tripleKillsNode.asInt();
    }

    private static Integer fromJsonToQuadKills(JsonNode json) {
        JsonNode quadKillsNode = json.findValue("quadKills");
        return quadKillsNode == null ? null : quadKillsNode.asInt();
    }

    private static Integer fromJsonToPentaKills(JsonNode json) {
        JsonNode pentaKillsNode = json.findValue("pentaKills");
        return pentaKillsNode == null ? null : pentaKillsNode.asInt();
    }

    private static TeamSide fromJsonToTeamSide(JsonNode json) {
        JsonNode teamSideNode = json.findValue("teamSide");
        if (teamSideNode == null) return null;
        String teamSideName = teamSideNode.asText();
        TeamSide teamSide = TeamSide.fromTeamSideName(teamSideName);
        if (teamSide == null) {
            throw new InvalidTeamsException("Invalid team side: " + teamSideName);
        }
        return teamSide;
    }

    private static Boolean fromJsonToWon(JsonNode json) {
        JsonNode wonNode = json.findValue("won");
        return wonNode != null && wonNode.asBoolean();
    }

    private static Integer fromJsonToMmrDelta(JsonNode json) {
        JsonNode mmrDeltaNode = json.findValue("mmrDelta");
        return mmrDeltaNode == null ? null : mmrDeltaNode.asInt();
    }
}
