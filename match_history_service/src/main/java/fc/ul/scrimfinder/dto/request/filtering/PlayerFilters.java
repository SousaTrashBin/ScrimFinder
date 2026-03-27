package fc.ul.scrimfinder.dto.request.filtering;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fc.ul.scrimfinder.util.Champion;
import fc.ul.scrimfinder.util.Role;
import fc.ul.scrimfinder.util.TeamSide;
import fc.ul.scrimfinder.util.interval.DoubleInterval;
import fc.ul.scrimfinder.util.interval.IntegerInterval;
import java.util.List;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class PlayerFilters {
    private String puuid;
    private String playerName;
    private String playerTag;
    private IntegerInterval kills;
    private IntegerInterval deaths;
    private IntegerInterval assists;
    private IntegerInterval healing;
    private IntegerInterval damageToPlayers;
    private IntegerInterval wards;
    private IntegerInterval gold;
    private Role role;
    private List<Champion> champions;
    private DoubleInterval csPerMinute;
    private IntegerInterval killedMinions;
    private IntegerInterval tripleKills;
    private IntegerInterval quadKills;
    private IntegerInterval pentaKills;
    private TeamSide teamSide;
    private Boolean won;
    private IntegerInterval mmrDelta;

    public static PlayerFilters valueOf(String value) {
        if (value == null || value.isBlank()) return null;

        ObjectMapper mapper = new ObjectMapper();
        JsonNode playerFilters;
        try {
            playerFilters = mapper.readTree(value);
        } catch (Exception x) {
            throw new IllegalArgumentException("Invalid player object format: " + value);
        }

        String puuid = fromJsonToField(playerFilters, "puuid", JsonNode::asText);
        String playerName = fromJsonToField(playerFilters, "playerName", JsonNode::asText);
        String playerTag = fromJsonToField(playerFilters, "playerTag", JsonNode::asText);
        IntegerInterval kills = fromJsonToField(playerFilters, "kills", PlayerFilters::getMinMaxInt);
        IntegerInterval deaths = fromJsonToField(playerFilters, "deaths", PlayerFilters::getMinMaxInt);
        IntegerInterval assists =
                fromJsonToField(playerFilters, "assists", PlayerFilters::getMinMaxInt);
        IntegerInterval healing =
                fromJsonToField(playerFilters, "healing", PlayerFilters::getMinMaxInt);
        IntegerInterval damageToPlayers =
                fromJsonToField(playerFilters, "damageToPlayers", PlayerFilters::getMinMaxInt);
        IntegerInterval wards = fromJsonToField(playerFilters, "wards", PlayerFilters::getMinMaxInt);
        IntegerInterval gold = fromJsonToField(playerFilters, "gold", PlayerFilters::getMinMaxInt);
        Role role = fromJsonToField(playerFilters, "role", node -> Role.fromRoleName(node.asText()));
        List<Champion> champions =
                fromJsonToField(
                        playerFilters,
                        "champions",
                        node ->
                                StreamSupport.stream(node.spliterator(), true)
                                        .map(championNode -> Champion.fromChampionName(championNode.asText()))
                                        .toList());
        DoubleInterval csPerMinute =
                fromJsonToField(playerFilters, "csPerMinute", PlayerFilters::getMinMaxDouble);
        IntegerInterval killedMinions =
                fromJsonToField(playerFilters, "killedMinions", PlayerFilters::getMinMaxInt);
        IntegerInterval tripleKills =
                fromJsonToField(playerFilters, "tripleKills", PlayerFilters::getMinMaxInt);
        IntegerInterval quadKills =
                fromJsonToField(playerFilters, "quadKills", PlayerFilters::getMinMaxInt);
        IntegerInterval pentaKills =
                fromJsonToField(playerFilters, "pentaKills", PlayerFilters::getMinMaxInt);
        TeamSide teamSide =
                fromJsonToField(
                        playerFilters, "teamSide", node -> TeamSide.fromTeamSideName(node.asText()));
        Boolean won = fromJsonToField(playerFilters, "won", JsonNode::asBoolean);
        IntegerInterval mmrDelta =
                fromJsonToField(playerFilters, "mmrDelta", PlayerFilters::getMinMaxInt);

        return new PlayerFilters(
                puuid,
                playerName,
                playerTag,
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

    private static IntegerInterval getMinMaxInt(JsonNode json) {
        return new IntegerInterval(
                fromJsonToField(json, "min", JsonNode::asInt),
                fromJsonToField(json, "max", JsonNode::asInt));
    }

    private static DoubleInterval getMinMaxDouble(JsonNode json) {
        return new DoubleInterval(
                fromJsonToField(json, "min", JsonNode::asDouble),
                fromJsonToField(json, "max", JsonNode::asDouble));
    }
}
