package fc.ul.scrimfinder.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fc.ul.scrimfinder.exception.InvalidRoleException;
import fc.ul.scrimfinder.exception.InvalidTeamsException;
import fc.ul.scrimfinder.util.*;
import io.smallrye.common.constraint.NotNull;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.QueryParam;

public record PlayerStatsDTO(
        @BeanParam
        @NotNull
        @Valid
        RiotId riotId,

        @BeanParam
        @NotNull
        @Valid
        Rank rank,

        @QueryParam("kills")
        @Min(value = 0, message = "A player can only have more than or equal to 0 kills")
        Integer kills,

        @QueryParam("deaths")
        @Min(value = 0, message = "A player can only have more than or equal to 0 deaths")
        Integer deaths,

        @QueryParam("assists")
        @Min(value = 0, message = "A player can only have more than or equal to 0 assists")
        Integer assists,

        @QueryParam("healing")
        @Min(value = 0, message = "A player can only have more than or equal to 0 healing")
        Integer healing,

        @QueryParam("damageToPlayers")
        @Min(value = 0, message = "A player can only have more than or equal to 0 damage dealt to players")
        Integer damageToPlayers,

        @QueryParam("wards")
        @Min(value = 0, message = "A player can only place more than or equal to 0 wards")
        Integer wards,

        @QueryParam("gold")
        @Min(value = 0, message = "A player can only have more than or equal to 0 gold")
        Integer gold,

        @BeanParam
        Role role,

        @QueryParam("champion")
        String champion,

        @QueryParam("csPerMinute")
        @Min(value = 0, message = "A player can only have more than or equal to 0 cs per minute")
        Double csPerMinute,

        @QueryParam("killedMinions")
        @Min(value = 0, message = "A player can only have more than or equal to 0 killed minions")
        Integer killedMinions,

        @QueryParam("tripleKills")
        @Min(value = 0, message = "A player can only have more than or equal to 0 triple kills")
        Integer tripleKills,

        @QueryParam("quadKills")
        @Min(value = 0, message = "A player can only have more than or equal to 0 quad kills")
        Integer quadKills,

        @QueryParam("pentaKills")
        @Min(value = 0, message = "A player can only have more than or equal to 0 penta kills")
        Integer pentaKills,

        @QueryParam("side")
        TeamSide side,

        @QueryParam("won")
        @NotNull
        Boolean won
) {
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
        Rank rank = fromJsonToRank(playerStats);
        Integer kills = fromJsonToKills(playerStats);
        Integer deaths = fromJsonToDeaths(playerStats);
        Integer assists = fromJsonToAssists(playerStats);
        Integer healing = fromJsonToHealing(playerStats);
        Integer damageToPlayers = fromJsonToDamageToPlayers(playerStats);
        Integer wards = fromJsonToWards(playerStats);
        Integer gold = fromJsonToGold(playerStats);
        Role role = fromJsonToRole(playerStats);
        String champion = fromJsonToChampion(playerStats);
        Double csPerMinute = fromJsonToCsPerMinute(playerStats);
        Integer killedMinions = fromJsonToKilledMinions(playerStats);
        Integer tripleKills = fromJsonToTripleKills(playerStats);
        Integer quadKills = fromJsonToQuadKills(playerStats);
        Integer pentaKills = fromJsonToPentaKills(playerStats);
        TeamSide teamSide = fromJsonToTeamSide(playerStats);
        Boolean won = fromJsonToWon(playerStats);

        return new PlayerStatsDTO(
                riotId,
                rank,
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
                won
        );
    }

    private static RiotId fromJsonToRiotId(JsonNode json) {
        JsonNode riotIdNode = json.get("riotId");
        if (riotIdNode == null) {
            throw new IllegalArgumentException("Riot ID is required: " + json);
        }

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

        return new RiotId(playerName, playerTag, playerIcon);
    }

    private static Rank fromJsonToRank(JsonNode json) {
        JsonNode rankNode = json.findValue("rank");
        if (rankNode == null) {
            throw new IllegalArgumentException("Rank is required: " + json);
        }
        return Rank.valueOf(rankNode.asText());
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
        Role role = Role.fromRoleName(roleName);
        if (role == null) {
            throw new InvalidRoleException("Invalid role: " + roleName);
        }
        return role;
    }

    private static String fromJsonToChampion(JsonNode json) {
        JsonNode championNode = json.findValue("champion");
        return championNode == null ? null : championNode.asText();
    }

    private static Double fromJsonToCsPerMinute(JsonNode json) {
        JsonNode csPerMinuteNode = json.findValue("csPerMinute");
        return csPerMinuteNode == null ? null : csPerMinuteNode.asDouble();
    }

    private static Integer fromJsonToKilledMinions(JsonNode json) {
        JsonNode killedMinionsNode = json.findValue("killedMinions");
        return killedMinionsNode == null ? null : killedMinionsNode.asInt();
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
}
