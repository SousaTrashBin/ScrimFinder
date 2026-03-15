package fc.ul.scrimfinder.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fc.ul.scrimfinder.util.SortColumn;
import fc.ul.scrimfinder.util.SortDirection;

public record SortParamDTO(
        SortColumn column,
        SortDirection direction
) {
    public static SortParamDTO valueOf(String value) {
        if (value == null || value.isBlank()) return null;

        ObjectMapper mapper = new ObjectMapper();
        JsonNode sortParam;
        try {
            sortParam = mapper.readTree(value);
        } catch (Exception x) {
            throw new IllegalArgumentException("Invalid sort object format: " + value);
        }

        String columnName = sortParam.get("column").asText();
        SortColumn column = SortColumn.fromColumnName(columnName);
        if (column == null) {
            throw new IllegalArgumentException("Invalid sort column: " + columnName);
        }

        JsonNode directionNode = sortParam.findValue("direction");
        SortDirection direction = SortDirection.ASC;
        if (directionNode != null) {
            String directionName = directionNode.asText();
            direction = SortDirection.fromDirectionName(directionName);
            if (direction == null) {
                throw new IllegalArgumentException("Invalid sort direction: " + directionName);
            }
        }

        return new SortParamDTO(column, direction);
    }
}
