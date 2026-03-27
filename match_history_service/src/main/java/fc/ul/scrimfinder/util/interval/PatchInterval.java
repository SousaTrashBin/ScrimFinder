package fc.ul.scrimfinder.util.interval;

import jakarta.validation.constraints.Pattern;
import lombok.*;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PatchInterval implements Interval<String> {
    @Schema(defaultValue = "16.5")
    @Pattern(
            regexp = "\\d+(\\.\\d+)*",
            message = "Match patch must have the form X.X with X being any number")
    private String min;

    @Schema(defaultValue = "16.5")
    @Pattern(
            regexp = "\\d+(\\.\\d+)*",
            message = "Match patch must have the form X.X with X being any number")
    private String max;
}
