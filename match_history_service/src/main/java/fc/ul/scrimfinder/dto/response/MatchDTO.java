package fc.ul.scrimfinder.dto.response;

import fc.ul.scrimfinder.dto.request.PlayerStatsDTO;
import fc.ul.scrimfinder.dto.request.TeamStatsDTO;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class MatchDTO {
    private String riotMatchId;
    private UUID queueId;
    private String patch;

    @Schema(
            description =
                    "Reflected as the number of seconds since January 1st, 1970 at UTC (Unix timestamp)")
    private Long gameCreation;

    private Long gameDuration;
    private List<PlayerStatsDTO> players;
    private List<TeamStatsDTO> teams;
}
