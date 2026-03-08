package fc.ul.scrimfinder.dto.response;

import fc.ul.scrimfinder.util.MatchState;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class MatchDTO {
    private Long id;
    private Long lobbyId;
    private MatchState state;
    private Set<Long> acceptedPlayerIds;
    private String externalGameId;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
