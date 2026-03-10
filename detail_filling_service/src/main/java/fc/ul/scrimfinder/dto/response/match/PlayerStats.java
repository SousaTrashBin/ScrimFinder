package fc.ul.scrimfinder.dto.response.match;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fc.ul.scrimfinder.util.RiotId;
import fc.ul.scrimfinder.util.Role;

public record PlayerStats(
        RiotId riotId,
        Integer playerIcon,
        Integer kills,
        Integer deaths,
        Integer assists,
        Integer healing,
        Integer damageToPlayers,
        Integer wards,
        Integer gold,
        Role role,
        String champion,
        Double csPerMinute,
        Integer killedMinions,
        Integer tripleKills,
        Integer quadKills,
        Integer pentaKills
) {
    public static PlayerStats valueOf(String value) {
        if (value == null || value.isBlank()) return null;

        ObjectMapper mapper = new ObjectMapper();
        JsonNode playerStats;
        try {
            playerStats = mapper.readTree(value);
        } catch (Exception x) {
            throw new IllegalArgumentException("Invalid player object format: " + value);
        }

        RiotId riotId = fromJsonToRiotId(playerStats);
        Integer playerIcon = fromJsonToPlayerIcon(playerStats);
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

        return new PlayerStats(
                riotId,
                playerIcon,
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
                pentaKills
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

        return new RiotId(playerName, playerTag);
    }

    private static Integer fromJsonToPlayerIcon(JsonNode json) {
        JsonNode playerIconNode = json.findValue("playerIcon");
        if  (playerIconNode == null) {
            return 0;
        }
        return playerIconNode.asInt();
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
}
