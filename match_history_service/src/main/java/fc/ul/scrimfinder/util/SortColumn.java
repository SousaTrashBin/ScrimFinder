package fc.ul.scrimfinder.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum SortColumn {
    @JsonProperty(value = "queueId")
    QUEUE_ID("queueId"),

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

    @JsonProperty(value = "patch")
    PATCH("patch"),

    @JsonProperty(value = "timeStart")
    TIME_START("timeStart"),

    @JsonProperty(value = "timeEnd")
    TIME_END("timeEnd");

    final String column;

    public static SortColumn fromColumnName(String name) {
        for (SortColumn sc : values()) {
            if (sc.column.equalsIgnoreCase(name)) {
                return sc;
            }
        }
        return null;
    }
}
