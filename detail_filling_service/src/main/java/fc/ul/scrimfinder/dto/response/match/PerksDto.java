package fc.ul.scrimfinder.dto.response.match;

import java.util.List;

public record PerksDto(
        PerkStatsDto statPerks,
        List<PerkStyleDto> styles
) {
}
