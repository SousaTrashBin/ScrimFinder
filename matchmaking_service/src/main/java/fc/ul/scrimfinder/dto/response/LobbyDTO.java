package fc.ul.scrimfinder.dto.response;

import fc.ul.scrimfinder.util.Region;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class LobbyDTO {
    private Long id;
    private Long queueId;
    private Region region;
    private List<MatchTicketDTO> tickets;
    private LocalDateTime createdAt;
}
