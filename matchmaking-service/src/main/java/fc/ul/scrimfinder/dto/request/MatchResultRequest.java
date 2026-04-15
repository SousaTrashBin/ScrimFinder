package fc.ul.scrimfinder.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

public record MatchResultRequest(
        @NotBlank String gameId, @NotNull UUID queueId, @NotEmpty Map<UUID, PlayerDelta> playerDeltas) {
    public record PlayerDelta(int winDelta, int lossDelta) {}
}
