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
public class DoubleInterval implements Interval<Double> {
    @QueryParam("min")
    private Double min;

    @QueryParam("max")
    private Double max;
}
