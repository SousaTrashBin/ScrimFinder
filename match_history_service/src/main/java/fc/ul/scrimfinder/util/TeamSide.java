package fc.ul.scrimfinder.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum TeamSide {
    @JsonProperty("blue")
    BLUE("blue"),

    @JsonProperty("red")
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
