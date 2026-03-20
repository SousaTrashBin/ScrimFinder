package fc.ul.scrimfinder.util;

import jakarta.enterprise.inject.Vetoed;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
public class RiotId {
    @QueryParam("playerName")
    @NotBlank(message = "The player name cannot be empty")
    private String playerName;

    @QueryParam("playerTag")
    @NotBlank(message = "The player tag cannot be empty")
    private String playerTag;

    @Min(value = 0, message = "The icon ID of the player must be greater or equal to 0")
    private Integer playerIcon;
}
