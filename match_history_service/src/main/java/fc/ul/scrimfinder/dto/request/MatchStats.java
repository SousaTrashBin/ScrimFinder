package fc.ul.scrimfinder.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import fc.ul.scrimfinder.domain.Team;
import fc.ul.scrimfinder.util.PatchInterval;
import fc.ul.scrimfinder.util.TimeInterval;
import io.quarkus.hibernate.orm.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.QueryParam;

import java.util.List;

public record MatchStats(
        @QueryParam("ranks")
        List<String> ranks,

        @QueryParam("champions")
        List<String> champions,

        @QueryParam("matchTripleKills")
        @Min(value = 0, message = "There can be no less than 0 total triple kills per match")
        Integer matchTripleKills,

        @QueryParam("matchQuadKills")
        @Min(value = 0, message = "There can be no less than 0 total quad kills per match")
        Integer matchQuadKills,

        @QueryParam("matchPentaKills")
        @Min(value = 0, message = "There can be no less than 0 total penta kills per match")
        Integer matchPentaKills,

        @BeanParam
        @Valid
        PatchInterval patchInterval,

        @BeanParam
        @Valid
        TimeInterval timeInterval,

        @QueryParam("teams")
        @Valid
        List<TeamStats> teams,

        @QueryParam("queueId")
        @Positive(message = "The queue ID must be positive")
        Long queueId,

        @QueryParam("players")
        @Valid
        List<PlayerStats> players
) {
}
