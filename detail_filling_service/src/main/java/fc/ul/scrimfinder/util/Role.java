package fc.ul.scrimfinder.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum Role {
    TOP("top"),
    JUNGLE("jungle"),
    MIDDLE("middle"),
    BOTTOM("bottom"),
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
