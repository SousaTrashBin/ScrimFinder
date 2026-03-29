package fc.ul.scrimfinder.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum Role {
    TOP("top"),
    JUNGLE("jungle"),
    MID("mid"),
    BOTTOM("bottom"),
    SUPPORT("support");

    final String roleName;

    public static Role fromRoleName(String name) {
        for (Role r : values()) {
            if (r.roleName.equalsIgnoreCase(name)) {
                return r;
            }
        }
        return switch (name.toUpperCase()) {
            case "MIDDLE" -> MID;
            case "UTILITY" -> SUPPORT;
            default -> null;
        };
    }
}
