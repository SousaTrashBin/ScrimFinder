package fc.ul.scrimfinder.dto.response;

import fc.ul.scrimfinder.dto.request.PlayerStats;
import fc.ul.scrimfinder.dto.request.TeamStats;
import java.time.LocalDateTime;
import java.util.List;

public record MatchDTO(
        Long id,
        String riotMatchId,
        Long queueId,
        LocalDateTime gameCreation,
        Long gameDuration,
        List<PlayerStats> players,
        List<TeamStats> teams
) {
}
