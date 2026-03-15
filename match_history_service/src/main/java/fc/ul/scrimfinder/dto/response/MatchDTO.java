package fc.ul.scrimfinder.dto.response;

import fc.ul.scrimfinder.dto.request.MatchStatsDTO;

public record MatchDTO(
        Long id,
        MatchStatsDTO matchStats
) {
}
