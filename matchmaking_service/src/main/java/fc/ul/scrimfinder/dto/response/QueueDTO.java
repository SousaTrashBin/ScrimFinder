package fc.ul.scrimfinder.dto.response;

import fc.ul.scrimfinder.util.MatchmakingMode;
import lombok.Data;

@Data
public class QueueDTO {
    private Long id;
    private String name;
    private String namespace;
    private int requiredPlayers;
    private boolean isRoleQueue;
    private MatchmakingMode mode;
    private int mmrWindow;
}
