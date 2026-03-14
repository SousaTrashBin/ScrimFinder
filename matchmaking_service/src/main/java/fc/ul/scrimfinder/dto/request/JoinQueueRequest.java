package fc.ul.scrimfinder.dto.request;

import fc.ul.scrimfinder.util.Role;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class JoinQueueRequest {
    @NotNull
    private Long playerId;
    @NotNull
    private Long queueId;
    private Role role = Role.NONE;
}
