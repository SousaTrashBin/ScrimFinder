package fc.ul.scrimfinder.util.interval;

import jakarta.ws.rs.QueryParam;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TimeInterval {
    @QueryParam("since")
    private LocalDateTime min;

    @QueryParam("until")
    private LocalDateTime max;
}
