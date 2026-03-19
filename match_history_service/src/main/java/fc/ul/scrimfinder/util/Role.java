package fc.ul.scrimfinder.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum Role {
    @JsonProperty(value = "top")
    TOP("top"),

    @JsonProperty(value = "jungle")
    JUNGLE("jungle"),

    @JsonProperty(value = "middle")
    MIDDLE("middle"),

    @JsonProperty(value = "bottom")
    BOTTOM("bottom"),

    @JsonProperty(value = "utility")
    UTILITY("utility");

    final String roleName;

    public static Role fromRoleName(String name) {
        for (Role r : values()) {
            if (r.roleName.equalsIgnoreCase(name)) {
                return r;
            }
        }
        return null;
    }
}
