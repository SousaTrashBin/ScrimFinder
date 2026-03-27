package fc.ul.scrimfinder.util.interval;

import lombok.*;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DoubleInterval implements Interval<Double> {
    private Double min;
    private Double max;
}
