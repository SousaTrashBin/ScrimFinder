package fc.ul.scrimfinder.util;

import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.QueryParam;

public record PatchInterval(
        @QueryParam("sincePatch")
        @Pattern(regexp = "\\d+\\.\\d+", message = "Match patch must have the form X.X with X being any number")
        String sincePatch,

        @QueryParam("untilPatch")
        @Pattern(regexp = "\\d+\\.\\d+", message = "Match patch must have the form X.X with X being any number")
        String untilPatch
) {
}
