package fc.ul.scrimfinder.dto.response.player;

import java.util.Set;

public record PlayerDto(
        Set<LeagueEntryDto> entries
) {
}
