package fc.ul.scrimfinder.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum SortColumn {
    QUEUE_ID("queueId", "queueId"),
    PATCH("patch", "patch"),
    TIME("time", "gameCreation"),
    BLUE_KILLS("blueKills", "blue.teamKills"),
    BLUE_DEATHS("blueDeaths", "blue.teamDeaths"),
    BLUE_ASSISTS("blueAssists", "blue.teamAssists"),
    BLUE_HEALING("blueHealing", "blue.teamHealing"),
    RED_KILLS("redKills", "red.teamKills"),
    RED_DEATHS("redDeaths", "red.teamDeaths"),
    RED_ASSISTS("redAssists", "red.teamAssists"),
    RED_HEALING("redHealing", "red.teamHealing"),
    ;

    final String column;
    final String fieldName;

    public static SortColumn fromColumnName(String name) {
        for (SortColumn sc : values()) {
            if (sc.column.equalsIgnoreCase(name)) {
                return sc;
            }
        }
        return null;
    }
}
