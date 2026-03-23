package fc.ul.scrimfinder.dto.request.filtering;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fc.ul.scrimfinder.util.Champion;
import fc.ul.scrimfinder.util.Role;
import fc.ul.scrimfinder.util.TeamSide;
import fc.ul.scrimfinder.util.interval.NumberInterval;
import jakarta.ws.rs.QueryParam;
import java.util.List;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class PlayerFilters {
    @QueryParam("playerName")
    private String playerName;

    @QueryParam("playerTag")
    private String playerTag;

    @QueryParam("playerIcon")
    private Integer playerIcon;

    @QueryParam("kills")
    private NumberInterval kills;

    @QueryParam("deaths")
    private NumberInterval deaths;

    @QueryParam("assists")
    private NumberInterval assists;

    @QueryParam("healing")
    private NumberInterval healing;

    @QueryParam("damageToPlayers")
    private NumberInterval damageToPlayers;

    @QueryParam("wards")
    private NumberInterval wards;

    @QueryParam("gold")
    private NumberInterval gold;

    @QueryParam("role")
    private Role role;

    @QueryParam("champions")
    private List<Champion> champions;

    @QueryParam("csPerMinute")
    private NumberInterval csPerMinute;

    @QueryParam("killedMinions")
    private NumberInterval killedMinions;

    @QueryParam("tripleKills")
    private NumberInterval tripleKills;

    @QueryParam("quadKills")
    private NumberInterval quadKills;

    @QueryParam("pentaKills")
    private NumberInterval pentaKills;

    @QueryParam("side")
    private TeamSide side;

    @QueryParam("won")
    private Boolean won;

    @QueryParam("mmrDelta")
    private NumberInterval mmrDelta;

    public static PlayerFilters valueOf(String value) {
        if (value == null || value.isBlank()) return null;

        ObjectMapper mapper = new ObjectMapper();
        JsonNode playerFilters;
        try {
            playerFilters = mapper.readTree(value);
        } catch (Exception x) {
            throw new IllegalArgumentException("Invalid player object format: " + value);
        }

        String playerName = fromJsonToField(playerFilters, "playerName", JsonNode::asText);
        String playerTag = fromJsonToField(playerFilters, "playerTag", JsonNode::asText);
        Integer playerIcon = fromJsonToField(playerFilters, "playerIcon", JsonNode::asInt);
        NumberInterval kills = fromJsonToField(playerFilters, "kills", PlayerFilters::getMinMaxInt);
        NumberInterval deaths = fromJsonToField(playerFilters, "deaths", PlayerFilters::getMinMaxInt);
        NumberInterval assists = fromJsonToField(playerFilters, "assists", PlayerFilters::getMinMaxInt);
        NumberInterval healing = fromJsonToField(playerFilters, "healing", PlayerFilters::getMinMaxInt);
        NumberInterval damageToPlayers =
                fromJsonToField(playerFilters, "damageToPlayers", PlayerFilters::getMinMaxInt);
        NumberInterval wards = fromJsonToField(playerFilters, "wards", PlayerFilters::getMinMaxInt);
        NumberInterval gold = fromJsonToField(playerFilters, "gold", PlayerFilters::getMinMaxInt);
        Role role = fromJsonToField(playerFilters, "role", node -> Role.fromRoleName(node.asText()));
        List<Champion> champions =
                fromJsonToField(
                        playerFilters,
                        "champions",
                        node ->
                                StreamSupport.stream(node.spliterator(), true)
                                        .map(championNode -> Champion.fromChampionName(championNode.asText()))
                                        .toList());
        NumberInterval csPerMinute =
                fromJsonToField(playerFilters, "csPerMinute", PlayerFilters::getMinMaxDouble);
        NumberInterval killedMinions =
                fromJsonToField(playerFilters, "killedMinions", PlayerFilters::getMinMaxInt);
        NumberInterval tripleKills =
                fromJsonToField(playerFilters, "tripleKills", PlayerFilters::getMinMaxInt);
        NumberInterval quadKills =
                fromJsonToField(playerFilters, "quadKills", PlayerFilters::getMinMaxInt);
        NumberInterval pentaKills =
                fromJsonToField(playerFilters, "pentaKills", PlayerFilters::getMinMaxInt);
        TeamSide teamSide =
                fromJsonToField(
                        playerFilters, "teamSide", node -> TeamSide.fromTeamSideName(node.asText()));
        Boolean won = fromJsonToField(playerFilters, "won", JsonNode::asBoolean);
        NumberInterval mmrDelta =
                fromJsonToField(playerFilters, "mmrDelta", PlayerFilters::getMinMaxInt);

        return new PlayerFilters(
                playerName,
                playerTag,
                playerIcon,
                kills,
                deaths,
                assists,
                healing,
                damageToPlayers,
                wards,
                gold,
                role,
                champions,
                csPerMinute,
                killedMinions,
                tripleKills,
                quadKills,
                pentaKills,
                teamSide,
                won,
                mmrDelta);
    }

    private static <T> T fromJsonToField(JsonNode json, String field, Function<JsonNode, T> toField) {
        JsonNode targetNode = json.findValue(field);
        return targetNode == null ? null : toField.apply(targetNode);
    }

    private static NumberInterval getMinMaxInt(JsonNode json) {
        return new NumberInterval(
                fromJsonToField(json, "min", JsonNode::asInt),
                fromJsonToField(json, "max", JsonNode::asInt));
    }

    private static NumberInterval getMinMaxDouble(JsonNode json) {
        return new NumberInterval(
                fromJsonToField(json, "min", JsonNode::asDouble),
                fromJsonToField(json, "max", JsonNode::asDouble));
    }
}
