package fc.ul.scrimfinder.util;

import jakarta.validation.constraints.Pattern;
import jakarta.enterprise.inject.Vetoed;
import jakarta.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Vetoed
public class PatchInterval {
    @QueryParam("sincePatch")
    @Pattern(regexp = "\\d+\\.\\d+", message = "Match patch must have the form X.X with X being any number")
    private String sincePatch;

    @QueryParam("untilPatch")
    @Pattern(regexp = "\\d+\\.\\d+", message = "Match patch must have the form X.X with X being any number")
    private String untilPatch;
}
