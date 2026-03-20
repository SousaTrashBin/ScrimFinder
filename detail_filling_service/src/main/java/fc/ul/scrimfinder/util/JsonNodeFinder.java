package fc.ul.scrimfinder.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public record JsonNodeFinder(JsonNode jsonNode) {
    public JsonNodeFinder jsonGetOrThrow(
            String fieldName, Class<? extends RuntimeException> exception) {
        JsonNode destination = this.jsonNode.get(fieldName);
        if (destination == null) {
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
