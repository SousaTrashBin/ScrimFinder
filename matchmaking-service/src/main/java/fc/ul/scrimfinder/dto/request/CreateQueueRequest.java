package fc.ul.scrimfinder.dto.request;

import fc.ul.scrimfinder.util.MatchmakingMode;
import fc.ul.scrimfinder.util.Region;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateQueueRequest(
        @NotNull UUID id,
        @NotBlank String name,
        String namespace,
        @Min(2) int requiredPlayers,
        boolean isRoleQueue,
        @NotNull MatchmakingMode mode,
        @Min(0) int mmrWindow,
        Region region) {}
