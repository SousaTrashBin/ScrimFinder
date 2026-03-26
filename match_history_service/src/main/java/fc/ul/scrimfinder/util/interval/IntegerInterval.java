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
public class IntegerInterval implements Interval<Integer> {
    @QueryParam("min")
    private Integer min;

    @QueryParam("max")
    private Integer max;
}
