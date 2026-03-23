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
public class NumberInterval {
    @QueryParam("min")
    private Number min;

    @QueryParam("max")
    private Number max;
}
