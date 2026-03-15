package fc.ul.scrimfinder.dto.response.match;

import java.time.LocalDateTime;
import java.util.List;

public record MatchStatsDTO(
        String riotMatchId, // including match region
        Long queueId,
        String patch,
        LocalDateTime gameCreation,
        Long gameDuration,
        List<PlayerStatsDTO> players,
        List<TeamStatsDTO> teams
) {
}
