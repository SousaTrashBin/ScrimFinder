package fc.ul.scrimfinder.dto.response;

import fc.ul.scrimfinder.util.MatchState;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import lombok.Data;

@Data
public class MatchDTO {
    private UUID id;
    private UUID lobbyId;
    private MatchState state;
    private Set<UUID> acceptedPlayerIds;
    private String externalGameId;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
