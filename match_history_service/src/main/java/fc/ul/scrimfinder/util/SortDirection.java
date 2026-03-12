package fc.ul.scrimfinder.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum SortDirection {
    @JsonProperty(value = "asc")
    ASC("asc"),

    @JsonProperty(value = "desc")
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
