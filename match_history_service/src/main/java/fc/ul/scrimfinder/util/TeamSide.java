package fc.ul.scrimfinder.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum TeamSide {
    BLUE("blue"),

    RED("red");

    final String teamSideName;

    public static TeamSide fromTeamSideName(String name) {
        for (TeamSide ts : values()) {
            if (ts.teamSideName.equalsIgnoreCase(name)) {
                return ts;
            }
        }
        return null;
    }
}
