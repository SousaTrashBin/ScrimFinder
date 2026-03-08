package fc.ul.scrimfinder.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

public record MatchAddDto(
        @Positive(message = "The Riot ID of this match must be positive")
        Long riotMatchId,

        @Valid
        MatchStats matchStats
) {
}
