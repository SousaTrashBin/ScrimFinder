package fc.ul.scrimfinder.dto.response.match;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record MatchDTO(
        @NotBlank(message = "The Riot ID of the match is required")
        String riotMatchId,

        @Valid
        MatchStatsDTO matchStats
) {
}
