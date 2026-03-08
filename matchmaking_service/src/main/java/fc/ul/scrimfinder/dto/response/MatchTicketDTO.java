package fc.ul.scrimfinder.dto.response;

import fc.ul.scrimfinder.util.Role;
import fc.ul.scrimfinder.util.TicketStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MatchTicketDTO {
    private Long id;
    private Long playerId;
    private Long queueId;
    private Role role;
    private TicketStatus status;
    private int mmr;
    private LocalDateTime createdAt;
    private Long lobbyId;
}
