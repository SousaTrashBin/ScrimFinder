package fc.ul.scrimfinder.util.interval;

import jakarta.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LongInterval implements Interval<Long> {
    @QueryParam("min")
    private Long min;

    @QueryParam("max")
    private Long max;
}
