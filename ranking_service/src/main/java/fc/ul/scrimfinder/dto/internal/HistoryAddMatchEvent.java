package fc.ul.scrimfinder.dto.internal;

import java.util.Map;

public record HistoryAddMatchEvent(
        String gameId, String queueId, Map<String, Integer> playerMmrGains) {}
