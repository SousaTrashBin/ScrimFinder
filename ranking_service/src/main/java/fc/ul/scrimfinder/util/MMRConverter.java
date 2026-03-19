package fc.ul.scrimfinder.util;

import fc.ul.scrimfinder.dto.response.RankDTO;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MMRConverter {

    public int convertRankToMMR(RankDTO rank) {
        if (rank == null || rank.tier() == null) {
            return 1000;
        }

        int baseMmr =
                switch (rank.tier()) {
                    case IRON -> 400;
                    case BRONZE -> 800;
                    case SILVER -> 1200;
                    case GOLD -> 1600;
                    case PLATINUM -> 2000;
                    case EMERALD -> 2400;
                    case DIAMOND -> 2800;
                    case MASTER, GRANDMASTER, CHALLENGER -> 3200;
                };

        int divisionBonus = rank.division() != null ? (5 - rank.division()) * 100 : 0;
        return baseMmr + divisionBonus + rank.lps();
    }
}
