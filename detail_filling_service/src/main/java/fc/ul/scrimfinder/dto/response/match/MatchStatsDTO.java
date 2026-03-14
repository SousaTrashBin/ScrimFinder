package fc.ul.scrimfinder.dto.response.match;

import fc.ul.scrimfinder.util.PatchInterval;
import fc.ul.scrimfinder.util.TimeInterval;

import java.util.List;

public record MatchStatsDTO(
        List<String> ranks,
        List<String> champions,
        Integer matchTripleKills,
        Integer matchQuadKills,
        Integer matchPentaKills,
        PatchInterval patchInterval,
        TimeInterval timeInterval,
        List<TeamStats> teams,
        Long queueId,
        List<PlayerStats> players
) {
}
