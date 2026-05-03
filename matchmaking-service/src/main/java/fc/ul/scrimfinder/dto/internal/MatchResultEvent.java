package fc.ul.scrimfinder.dto.internal;

import java.util.Map;

public record MatchResultEvent(
        String gameId, String queueId, Map<String, PlayerDeltaEvent> playerDeltas) {
    public record PlayerDeltaEvent(int winDelta, int lossDelta) {}
}
