package fc.ul.scrimfinder.dto.response;

import fc.ul.scrimfinder.dto.request.PlayerStatsDTO;
import fc.ul.scrimfinder.dto.request.TeamStatsDTO;
import java.time.LocalDateTime;
import java.util.List;

public record MatchDTO(
        String riotMatchId,
        Long queueId,
        String patch,
        LocalDateTime gameCreation,
        Long gameDuration,
        List<PlayerStatsDTO> players,
        List<TeamStatsDTO> teams) {}
