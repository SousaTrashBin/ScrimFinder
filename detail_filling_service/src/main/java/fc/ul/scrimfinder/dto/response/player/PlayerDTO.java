package fc.ul.scrimfinder.dto.response.player;

import java.util.Set;

public record PlayerDTO(
        Set<LeagueEntryDTO> entries
) {
}
