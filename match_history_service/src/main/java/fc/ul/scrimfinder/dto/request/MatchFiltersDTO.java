package fc.ul.scrimfinder.dto.request;

import fc.ul.scrimfinder.util.Champion;
import fc.ul.scrimfinder.util.PatchInterval;
import fc.ul.scrimfinder.util.TimeInterval;
import jakarta.validation.Valid;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.QueryParam;
import java.util.List;

public record MatchFiltersDTO(
        @QueryParam("queueId") Long queueId,
        @QueryParam("champions") List<Champion> champions,
        @BeanParam @Valid PatchInterval patchInterval,
        @BeanParam @Valid TimeInterval timeInterval,
        @QueryParam("teams") List<@Valid TeamStatsDTO> teams,
        @QueryParam("players") List<@Valid PlayerStatsDTO> players) {}
