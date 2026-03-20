package fc.ul.scrimfinder.dto.response;

import fc.ul.scrimfinder.util.Region;
import fc.ul.scrimfinder.util.Role;
import fc.ul.scrimfinder.util.TicketStatus;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class MatchTicketDTO {
    private Long id;
    private Long playerId;
    private Long queueId;
    private Region region;
    private Role role;
    private TicketStatus status;
    private int mmr;
    private LocalDateTime createdAt;
    private Long lobbyId;
}
