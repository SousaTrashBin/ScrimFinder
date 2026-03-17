package fc.ul.scrimfinder.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public record PlayerMMRDelta(
        String puuid,
        Integer delta
) {
    public static PlayerMMRDelta valueOf(String value) {
        if (value == null || value.isBlank()) return null;

        ObjectMapper mapper = new ObjectMapper();
        JsonNode playerMMRDeltaNode;
        try {
            playerMMRDeltaNode = mapper.readTree(value);
        } catch (Exception x) {
            throw new IllegalArgumentException("Invalid player MMR delta object format: " + value);
        }

        String puuid = fromJsonToPuuid(playerMMRDeltaNode);
        Integer delta = fromJsonToDelta(playerMMRDeltaNode);

        return new PlayerMMRDelta(
                puuid,
                delta
        );
    }

    private static String fromJsonToPuuid(JsonNode json) {
        JsonNode puuidNode = json.findValue("puuid");
        return puuidNode == null ? null : puuidNode.asText();
    }

    private static Integer fromJsonToDelta(JsonNode json) {
        JsonNode deltaNode = json.findValue("delta");
        return deltaNode == null ? null : deltaNode.asInt();
    }
}
