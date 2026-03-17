package fc.ul.scrimfinder.dto.response.player;

import fc.ul.scrimfinder.util.Rank;

public record PlayerQueueStatsDTO(
        String queueType,
        Rank rank,
        Integer wins,
        Integer losses,
        Boolean hotStreak
) {
}
