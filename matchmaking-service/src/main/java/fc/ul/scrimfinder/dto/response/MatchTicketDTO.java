package fc.ul.scrimfinder.dto.response;

import fc.ul.scrimfinder.util.Region;
import fc.ul.scrimfinder.util.Role;
import fc.ul.scrimfinder.util.TicketStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;

@Data
public class MatchTicketDTO {
    private UUID id;
    private UUID playerId;
    private UUID queueId;
    private Region region;
    private Role role;
    private TicketStatus status;
    private int mmr;
    private LocalDateTime createdAt;
    private UUID lobbyId;
}
