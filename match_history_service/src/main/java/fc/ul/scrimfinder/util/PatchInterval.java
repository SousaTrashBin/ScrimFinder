package fc.ul.scrimfinder.util;

import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.QueryParam;

public record PatchInterval(
        @QueryParam("sincePatch")
        String sincePatch,

        @QueryParam("untilPatch")
        String untilPatch
) {
}
