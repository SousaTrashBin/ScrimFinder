package fc.ul.scrimfinder.util;

import jakarta.ws.rs.QueryParam;

public record RiotId(
        @QueryParam("playerName") String playerName,
        @QueryParam("playerTag") String playerTag
) {
}
