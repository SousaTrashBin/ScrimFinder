package fc.ul.scrimfinder.util.interval;

import lombok.*;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntegerInterval implements Interval<Integer> {
    private Integer min;
    private Integer max;
}
