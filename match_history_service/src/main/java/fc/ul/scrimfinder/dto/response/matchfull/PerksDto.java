package fc.ul.scrimfinder.dto.response.matchfull;

import java.util.List;

public record PerksDto(
        PerkStatsDto statPerks,
        List<PerkStyleDto> styles
) {
}
