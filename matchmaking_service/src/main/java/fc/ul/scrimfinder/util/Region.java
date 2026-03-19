package fc.ul.scrimfinder.util;

import lombok.Getter;

@Getter
public enum Region {
    BR("BR", "BR1"),
    EUNE("EUNE", "EUN1"),
    EUW("EUW", "EUW1"),
    LAN("LAN", "LA1"),
    LAS("LAS", "LA2"),
    NA("NA", "NA1"),
    OCE("OCE", "OC1"),
    RU("RU", "RU1"),
    TR("TR", "TR1"),
    ME("ME", "ME1"),
    JP("JP", "JP1"),
    KR("KR", "KR"),
    SEA("SEA", "SEA"),
    TW("TW", "TW2"),
    VN("VN", "VN2"),
    PBE("PBE", "PBE");

    private final String displayName;
    private final String internalId;

    Region(String displayName, String internalId) {
        this.displayName = displayName;
        this.internalId = internalId;
    }

    public static Region fromDisplayName(String displayName) {
        for (Region region : values()) {
            if (region.displayName.equalsIgnoreCase(displayName)) {
                return region;
            }
        }
        throw new IllegalArgumentException("Unknown region display name: " + displayName);
    }

    public static Region fromInternalId(String internalId) {
        for (Region region : values()) {
            if (region.internalId.equalsIgnoreCase(internalId)) {
                return region;
            }
        }
        throw new IllegalArgumentException("Unknown region internal ID: " + internalId);
    }
}
