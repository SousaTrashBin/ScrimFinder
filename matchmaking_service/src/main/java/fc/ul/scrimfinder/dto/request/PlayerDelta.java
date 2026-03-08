package fc.ul.scrimfinder.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerDelta {
    private int winDelta;
    private int lossDelta;
}
