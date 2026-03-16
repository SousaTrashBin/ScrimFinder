package fc.ul.scrimfinder.util;

import com.fasterxml.jackson.databind.JsonNode;

public record JsonNodeFinder(JsonNode jsonNode) {
    public JsonNodeFinder jsonGetOrThrow(String fieldName, Class<? extends RuntimeException> exception) {
        JsonNode destination = this.jsonNode.get(fieldName);
        if (destination != null) {
            return new JsonNodeFinder(destination);
        }
        try {
            throw (Exception) Class.forName(exception.getName()).getConstructor(String.class).newInstance("Missing field in Riot response: " + fieldName);
        } catch (Exception x) {
            throw new IllegalArgumentException("Invalid exception class while mapping from Riot response: " + exception.getName());
        }
    }
}
