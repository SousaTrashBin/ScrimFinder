package fc.ul.scrimfinder.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class LobbyDTO {
    private Long id;
    private Long queueId;
    private List<MatchTicketDTO> tickets;
    private LocalDateTime createdAt;
}
