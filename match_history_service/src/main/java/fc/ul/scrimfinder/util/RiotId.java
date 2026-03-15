package fc.ul.scrimfinder.util;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.QueryParam;

public record RiotId(
        @QueryParam("playerName")
        @NotBlank(message = "The player name cannot be empty")
        String playerName,

        @QueryParam("playerTag")
        @NotBlank(message = "The player tag cannot be empty")
        String playerTag,

        @Min(value = 0, message = "The icon ID of the player must be greater or equal to 0")
        Integer playerIcon
) {
}
