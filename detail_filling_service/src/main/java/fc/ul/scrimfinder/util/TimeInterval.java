package fc.ul.scrimfinder.util;

import java.time.LocalDateTime;

public record TimeInterval(
        LocalDateTime start,
        LocalDateTime end
) {
}
