package fc.ul.scrimfinder.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum Tier {
    UNRANKED("unranked"),
    IRON("iron"),
    BRONZE("bronze"),
    SILVER("silver"),
    GOLD("gold"),
    PLATINUM("platinum"),
    EMERALD("emerald"),
    DIAMOND("diamond"),
    MASTER("master"),
    GRANDMASTER("grandmaster"),
    CHALLENGER("challenger");

    final String tier;

    public static Tier fromTierName(String name) {
        for (Tier t : values()) {
            if (t.tier.equalsIgnoreCase(name)) {
                return t;
            }
        }
        return null;
    }
}
