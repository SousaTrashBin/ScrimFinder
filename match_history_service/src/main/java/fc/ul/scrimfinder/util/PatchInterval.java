package fc.ul.scrimfinder.util;

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
    private String sincePatch;

    @QueryParam("untilPatch")
    private String untilPatch;
}
