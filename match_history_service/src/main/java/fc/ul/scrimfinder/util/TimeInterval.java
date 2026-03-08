package fc.ul.scrimfinder.util;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.QueryParam;

import java.time.LocalDateTime;

public record TimeInterval(
        @QueryParam("since")
        LocalDateTime start,

        @QueryParam("until")
        LocalDateTime end
) {
}
