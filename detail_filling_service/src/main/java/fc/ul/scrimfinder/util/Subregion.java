package fc.ul.scrimfinder.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum Subregion {
    NA1("na1"),
    BR1("br1"),
    LA1("la1"),
    LA2("la2"),
    KR("kr"),
    JP1("jp1"),
    EUN1("eun1"),
    EUW1("euw1"),
    ME1("me1"),
    TR1("tr1"),
    RU("ru"),
    OC1("oc1"),
    SG2("sg2"),
    TW2("tw2"),
    VN2("vn2");

    final String subRegionName;

    public static Subregion fromSubregionName(String name) {
        for (Subregion sr : values()) {
            if (sr.subRegionName.equalsIgnoreCase(name)) {
                return sr;
            }
        }
        return null;
    }

    public Region toRegion() {
        return switch (this) {
            case NA1, BR1, LA1, LA2 -> Region.AMERICAS;
            case KR, JP1 -> Region.ASIA;
            case EUN1, EUW1, ME1, TR1, RU -> Region.EUROPE;
            case OC1, SG2, TW2, VN2 -> Region.SEA;
        };
    }
}
