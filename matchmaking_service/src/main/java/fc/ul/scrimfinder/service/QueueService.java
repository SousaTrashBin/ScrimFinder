package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.QueueDTO;
import fc.ul.scrimfinder.util.MatchmakingMode;
import fc.ul.scrimfinder.util.Region;
import java.util.UUID;

public interface QueueService {
    QueueDTO createQueue(
            UUID id,
            String name,
            String namespace,
            int requiredPlayers,
            boolean isRoleQueue,
            MatchmakingMode mode,
            int mmrWindow,
            Region region);

    QueueDTO getQueue(UUID id);
}
