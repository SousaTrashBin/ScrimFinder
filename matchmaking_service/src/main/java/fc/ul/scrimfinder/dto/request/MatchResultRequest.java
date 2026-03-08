package fc.ul.scrimfinder.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record MatchResultRequest(
        @NotBlank String gameId,
        @NotNull Long queueId,
        @NotEmpty Map<Long, PlayerDelta> playerDeltas
) {
    public record PlayerDelta(
            int winDelta,
            int lossDelta
    ) {
    }
}
