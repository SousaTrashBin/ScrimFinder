package fc.ul.scrimfinder.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum SortDirection {
    ASC("asc"),
    DESC("desc");

    final String direction;

    public static SortDirection fromDirectionName(String name) {
        for (SortDirection sd : values()) {
            if (sd.direction.equalsIgnoreCase(name)) {
                return sd;
            }
        }
        return null;
    }
}
