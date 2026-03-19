package fc.ul.scrimfinder.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum Region {
    AMERICAS("americas"),
    ASIA("asia"),
    EUROPE("europe"),
    SEA("sea");

    final String regionName;

    public static Region fromRegionName(String name) {
        for (Region r : values()) {
            if (r.regionName.equalsIgnoreCase(name)) {
                return r;
            }
        }
        return null;
    }
}
