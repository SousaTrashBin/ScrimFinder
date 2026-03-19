package fc.ul.scrimfinder.util;

import jakarta.enterprise.inject.Vetoed;
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
@Vetoed
public class TimeInterval {
    @QueryParam("since")
    private LocalDateTime start;

    @QueryParam("until")
    private LocalDateTime end;
}
