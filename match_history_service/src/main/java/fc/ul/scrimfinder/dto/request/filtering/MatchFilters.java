package fc.ul.scrimfinder.dto.request.filtering;

import fc.ul.scrimfinder.util.Champion;
import fc.ul.scrimfinder.util.interval.LongInterval;
import fc.ul.scrimfinder.util.interval.PatchInterval;
import jakarta.validation.Valid;
import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@NoArgsConstructor
@Getter
@Setter
public class MatchFilters {
    private Long queueId;
    private List<Champion> champions;
    @Valid private PatchInterval patch;
    @Valid private LongInterval time;
    private List<@Valid PlayerFilters> players;
    private List<@Valid TeamFilters> teams;
}
