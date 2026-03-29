package fc.ul.scrimfinder.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jboss.logging.Logger;

@NoArgsConstructor
@Setter
public class JsonNodeFinder {
    @Inject Logger logger;

    private JsonNode jsonNode;

    public JsonNodeFinder(JsonNode jsonNode) {
        this.jsonNode = jsonNode;
    }

    public JsonNode jsonNode() {
        return jsonNode;
    }

    public JsonNodeFinder jsonGetOrThrow(
            String fieldName, Class<? extends RuntimeException> exception) {
        JsonNode destination = this.jsonNode.get(fieldName);
        if (destination == null) {
            logger.error(
                    ColoredMessage.withColor("Missing field in Riot response: " + fieldName, LogColor.RED));
            throwException(exception, "Missing field in Riot response: " + fieldName);
        }
        return new JsonNodeFinder(destination);
    }

    public JsonNodeFinder fromStringOrThrow(
            String json, Class<? extends RuntimeException> exception) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return new JsonNodeFinder(mapper.readTree(json));
        } catch (Exception x) {
            logger.error(
                    ColoredMessage.withColor("Failed to convert JSON from string: " + json, LogColor.RED));
            throwException(exception, json);
        }
        return null;
    }

    private void throwException(Class<? extends RuntimeException> exception, String message) {
        try {
            throw (RuntimeException)
                    Class.forName(exception.getName()).getConstructor(String.class).newInstance(message);
        } catch (Exception x) {
            throw new IllegalArgumentException("Invalid exception class: " + exception.getName());
        }
    }
}
