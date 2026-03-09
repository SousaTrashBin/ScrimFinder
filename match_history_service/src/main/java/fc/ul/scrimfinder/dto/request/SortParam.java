package fc.ul.scrimfinder.dto.request;

import fc.ul.scrimfinder.util.SortColumn;
import fc.ul.scrimfinder.util.SortDirection;

public record SortParam(
        SortColumn column,
        SortDirection direction
) {
    public static SortParam valueOf(String value) {
        if (value == null || value.isBlank()) return null;

        SortDirection direction = SortDirection.ASC;
        String columnName = value;

        if (value.startsWith("-")) {
            direction = SortDirection.DESC;
            columnName = value.substring(1);
        } else if (value.startsWith("+")) {
            columnName = value.substring(1);
        }

        SortColumn column = SortColumn.fromColumnName(columnName);
        if (column == null) {
            throw new IllegalArgumentException("Invalid sort column: " + columnName);
        }

        return new SortParam(column, direction);
    }
}
