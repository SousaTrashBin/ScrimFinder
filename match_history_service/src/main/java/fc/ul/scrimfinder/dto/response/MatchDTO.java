package fc.ul.scrimfinder.dto.response;

import fc.ul.scrimfinder.dto.request.PlayerStatsDTO;
import fc.ul.scrimfinder.dto.request.TeamStatsDTO;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record MatchDTO(
        String riotMatchId,
        Long queueId,
        String patch,
        @Schema(
                        description =
                                "Reflected as the number of seconds since January 1st, 1970 at UTC (Unix timestamp)")
                Long gameCreation,
        Long gameDuration,
        List<PlayerStatsDTO> players,
        List<TeamStatsDTO> teams) {}
