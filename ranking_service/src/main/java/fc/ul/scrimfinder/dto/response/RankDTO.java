package fc.ul.scrimfinder.dto.response;

import fc.ul.scrimfinder.util.Tier;

public record RankDTO(Tier tier, Integer division, Integer lps) {}
