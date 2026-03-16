package fc.ul.scrimfinder.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum Division {
    I("I", 1),
    II("II", 2),
    III("III", 3),
    IV("IV", 4);

    final String divisionRoman;
    final int divisionInt;

    public static Division fromDivisionName(String name) {
        for (Division d : values()) {
            if (d.divisionRoman.equalsIgnoreCase(name)) {
                return d;
            }
        }
        return null;
    }
}
