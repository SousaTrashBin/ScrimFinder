package fc.ul.scrimfinder.util.interval;

import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PatchInterval {
    @QueryParam("sincePatch")
    @Pattern(
            regexp = "\\d+\\.\\d+",
            message = "Match patch must have the form X.X with X being any number")
    private String min;

    @QueryParam("untilPatch")
    @Pattern(
            regexp = "\\d+\\.\\d+",
            message = "Match patch must have the form X.X with X being any number")
    private String max;
}
