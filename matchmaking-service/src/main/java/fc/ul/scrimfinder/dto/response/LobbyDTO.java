package fc.ul.scrimfinder.dto.response;

import fc.ul.scrimfinder.util.Region;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class LobbyDTO {
    private UUID id;
    private UUID matchId;
    private UUID queueId;
    private Region region;
    private List<MatchTicketDTO> tickets;
    private LocalDateTime createdAt;
}
