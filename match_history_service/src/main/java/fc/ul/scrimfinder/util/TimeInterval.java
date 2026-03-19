package fc.ul.scrimfinder.util;

import jakarta.ws.rs.QueryParam;

import java.time.LocalDateTime;

public record TimeInterval(
        @QueryParam("since")
        LocalDateTime start,

        @QueryParam("until")
        LocalDateTime end
) {
}
