package fc.ul.scrimfinder.dto.request;

import fc.ul.scrimfinder.util.Role;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JoinQueueRequest {
    @NotNull private Long playerId;
    @NotNull private Long queueId;
    private Role role = Role.NONE;
}
