package fc.ul.scrimfinder.dto.request;

import fc.ul.scrimfinder.util.PatchInterval;
import fc.ul.scrimfinder.util.TimeInterval;
import jakarta.enterprise.inject.Vetoed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.QueryParam;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Vetoed
public class MatchStats {
    @QueryParam("ranks")
    private List<String> ranks;

    @QueryParam("champions")
    private List<String> champions;

    @QueryParam("matchTripleKills")
    @Min(value = 0, message = "There can be no less than 0 total triple kills per match")
    private Integer matchTripleKills;

    @QueryParam("matchQuadKills")
    @Min(value = 0, message = "There can be no less than 0 total quad kills per match")
    private Integer matchQuadKills;

    @QueryParam("matchPentaKills")
    @Min(value = 0, message = "There can be no less than 0 total penta kills per match")
    private Integer matchPentaKills;

    @BeanParam @Valid private PatchInterval patchInterval;

    @BeanParam @Valid private TimeInterval timeInterval;

    @QueryParam("teams")
    private List<@Valid TeamStats> teams;

    @QueryParam("queueId")
    @Positive(message = "The queue ID must be positive")
    private Long queueId;

    @QueryParam("players")
    private List<@Valid PlayerStats> players;
}
