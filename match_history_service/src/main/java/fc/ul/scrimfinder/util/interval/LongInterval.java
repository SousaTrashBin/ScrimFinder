package fc.ul.scrimfinder.util.interval;

import lombok.*;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LongInterval implements Interval<Long> {
    private Long min;
    private Long max;
}
