package fc.ul.scrimfinder.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record MatchAddDto(
        @NotBlank(message = "The Riot ID of the match is required")
        String riotMatchId,

        @Valid
        MatchStats matchStats
) {
}
