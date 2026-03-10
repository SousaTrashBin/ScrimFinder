package fc.ul.scrimfinder.dto.response.match;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record MatchDto(
        @NotBlank(message = "The Riot ID of the match is required")
        String riotMatchId,

        @Valid
        MatchStatsDto matchStats
) {
}
