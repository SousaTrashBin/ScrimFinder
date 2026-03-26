package fc.ul.scrimfinder.dto.request.filtering;

import fc.ul.scrimfinder.util.Champion;
import fc.ul.scrimfinder.util.interval.LongInterval;
import fc.ul.scrimfinder.util.interval.PatchInterval;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.QueryParam;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class MatchFilters {
    @QueryParam("queueId")
    private Long queueId;

    @QueryParam("champions")
    private List<Champion> champions;

    @BeanParam @Valid private PatchInterval patch;
    @BeanParam @Valid private LongInterval time;

    @QueryParam("players")
    @Size(max = 10, message = "There can be no more than 10 players in a match")
    private List<@Valid PlayerFilters> players;

    @QueryParam("teams")
    @Size(max = 2, message = "There can be no more than 2 teams in a match")
    private List<@Valid TeamFilters> teams;
}
