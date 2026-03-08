package fc.ul.scrimfinder.dto.request;

import fc.ul.scrimfinder.util.RiotId;
import fc.ul.scrimfinder.util.Role;
import io.smallrye.common.constraint.NotNull;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.QueryParam;

public record PlayerStats(
        @BeanParam
        @NotNull
        @Valid
        RiotId riotId,

        @QueryParam("kills")
        @Min(value = 0, message = "A player can only have more than or equal to 0 kills")
        Integer kills,

        @QueryParam("deaths")
        @Min(value = 0, message = "A player can only have more than or equal to 0 deaths")
        Integer deaths,

        @QueryParam("assists")
        @Min(value = 0, message = "A player can only have more than or equal to 0 assists")
        Integer assists,

        @QueryParam("healing")
        @Min(value = 0, message = "A player can only have more than or equal to 0 healing")
        Integer healing,

        @QueryParam("damageToPlayers")
        @Min(value = 0, message = "A player can only have more than or equal to 0 damage dealt to players")
        Integer damageToPlayers,

        @QueryParam("wards")
        @Min(value = 0, message = "A player can only place more than or equal to 0 wards")
        Integer wards,

        @QueryParam("gold")
        @Min(value = 0, message = "A player can only have more than or equal to 0 gold")
        Integer gold,

        @BeanParam
        Role role,

        @QueryParam("champion")
        String champion,

        @QueryParam("csPerMinute")
        @Min(value = 0, message = "A player can only have more than or equal to 0 cs per minute")
        Double csPerMinute,

        @QueryParam("killedMinions")
        @Min(value = 0, message = "A player can only have more than or equal to 0 killed minions")
        Integer killedMinions,

        @QueryParam("tripleKills")
        @Min(value = 0, message = "A player can only have more than or equal to 0 triple kills")
        Integer tripleKills,

        @QueryParam("quadKills")
        @Min(value = 0, message = "A player can only have more than or equal to 0 quad kills")
        Integer quadKills,

        @QueryParam("pentaKills")
        @Min(value = 0, message = "A player can only have more than or equal to 0 penta kills")
        Integer pentaKills
) {
    public static PlayerStats valueOf(String ignoreMe) {
        return new PlayerStats(null, 0, 0, 0, 0, 0, 0, 0, null, null, 0.0, 0, 0, 0, 0);
    }
}
