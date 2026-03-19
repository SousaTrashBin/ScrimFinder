package fc.ul.scrimfinder.dto.response.player;

import java.util.Set;

public record PlayerDTO(
        AccountDTO account,
        RegionDTO region,
        SummonerDTO summoner,
        Set<PlayerQueueStatsDTO> queues
) {
}
