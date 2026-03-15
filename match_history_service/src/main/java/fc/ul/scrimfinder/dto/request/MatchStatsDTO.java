package fc.ul.scrimfinder.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.List;

public record MatchStatsDTO(
        @NotBlank(message = "The Riot ID of the match is required")
        String riotMatchId,

        @Positive(message = "The queue ID must be positive")
        Long queueId,

        @Pattern(regexp = "\\d+\\.\\d+", message = "Match patch must have the form X.X with X being any number")
        String patch,

        @NotNull(message = "The start date of the match is required")
        LocalDateTime gameCreation,

        @Positive(message = "The duration of the match must be positive")
        Long gameDuration,

        @Valid
        List<PlayerStatsDTO> players,

        @Valid
        List<TeamStatsDTO> teams
) {
}
