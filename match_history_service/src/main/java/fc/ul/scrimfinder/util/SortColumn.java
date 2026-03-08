package fc.ul.scrimfinder.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum SortColumn {
    @JsonProperty(value = "rank")
    RANK("rank"),

    @JsonProperty(value = "champion")
    CHAMPION("champion"),

    @JsonProperty(value = "matchTripleKills")
    MATCH_TRIPLE_KILLS("matchTripleKills"),

    @JsonProperty(value = "matchQuadKills")
    MATCH_QUAD_KILLS("matchQuadKills"),

    @JsonProperty(value = "matchPentaKills")
    MATCH_PENTA_KILLS("matchPentaKills"),

    @JsonProperty(value = "patchStart")
    PATCH_START("patchStart"),

    @JsonProperty(value = "patchEnd")
    PATCH_END("patchEnd"),

    @JsonProperty(value = "timeStart")
    TIME_START("timeStart"),

    @JsonProperty(value = "timeEnd")
    TIME_END("timeEnd"),

    @JsonProperty(value = "queueId")
    QUEUE_ID("queueId");

    final String column;
}
