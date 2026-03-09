package fc.ul.scrimfinder.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MatchAddDto(
        @NotNull(message = "The Riot ID of the match is required")
        @Positive(message = "The Riot ID of the match must be positive")
        Long riotMatchId,

        @Valid
        MatchStats matchStats
) {
}
