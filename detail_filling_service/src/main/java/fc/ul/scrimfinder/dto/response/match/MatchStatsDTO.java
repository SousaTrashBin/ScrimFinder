package fc.ul.scrimfinder.dto.response.match;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record MatchStatsDTO(
        String riotMatchId, // including match region
        String patch,
        @Schema(
                        description =
                                "Reflected as the number of seconds since January 1st, 1970 at UTC (Unix timestamp)")
                Long gameCreation,
        Long gameDuration,
        List<PlayerStatsDTO> players,
        List<TeamStatsDTO> teams) {}
