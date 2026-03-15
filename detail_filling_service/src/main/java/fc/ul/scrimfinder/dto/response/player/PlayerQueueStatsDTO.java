package fc.ul.scrimfinder.dto.response.player;

import fc.ul.scrimfinder.util.Rank;

public record PlayerQueueStatsDTO(
        String queueId,
        String queueType,
        Rank rank,
        Integer wins,
        Integer losses,
        Boolean hotStreak,
        Boolean veteran,
        Boolean freshBlood,
        Boolean inactive
) {
}
