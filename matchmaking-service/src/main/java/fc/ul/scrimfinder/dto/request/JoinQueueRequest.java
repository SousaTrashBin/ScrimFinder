package fc.ul.scrimfinder.dto.request;

import fc.ul.scrimfinder.util.Role;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JoinQueueRequest {
    @NotNull private UUID playerId;
    @NotNull private UUID queueId;
    private Role role;
}
