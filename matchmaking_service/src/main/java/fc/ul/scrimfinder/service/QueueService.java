package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.QueueDTO;
import fc.ul.scrimfinder.util.MatchmakingMode;
import fc.ul.scrimfinder.util.Region;

public interface QueueService {
    QueueDTO createQueue(
            Long id,
            String name,
            String namespace,
            int requiredPlayers,
            boolean isRoleQueue,
            MatchmakingMode mode,
            int mmrWindow,
            Region region);

    QueueDTO getQueue(Long id);
}
