package fc.ul.scrimfinder.dto.response.player;

import java.util.Set;

public record PlayerDTO(
        String puuid,
        String name,
        String tag,
        Integer playerIcon,
        Set<PlayerQueueStatsDTO> queues
) {
}
