package fc.ul.scrimfinder.dto.response;

import fc.ul.scrimfinder.util.MatchmakingMode;
import fc.ul.scrimfinder.util.Region;
import java.util.UUID;
import lombok.Data;

@Data
public class QueueDTO {
    private UUID id;
    private String name;
    private Region region;
    private String namespace;
    private int requiredPlayers;
    private boolean isRoleQueue;
    private MatchmakingMode mode;
    private int mmrWindow;
}
