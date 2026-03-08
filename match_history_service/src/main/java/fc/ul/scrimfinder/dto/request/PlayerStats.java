package fc.ul.scrimfinder.dto.request;

import fc.ul.scrimfinder.util.RiotId;
import fc.ul.scrimfinder.util.Role;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.QueryParam;

public record PlayerStats(
        @BeanParam RiotId riotId,
        @QueryParam("kills") Integer kills,
        @QueryParam("deaths") Integer deaths,
        @QueryParam("assists") Integer assists,
        @QueryParam("healing") Integer healing,
        @QueryParam("damageToPlayers") Integer damageToPlayers,
        @QueryParam("wards") Integer wards,
        @QueryParam("gold") Integer gold,
        @BeanParam Role role,
        @QueryParam("champion") String champion,
        @QueryParam("csPerMinute") Double csPerMinute,
        @QueryParam("killedMinions") Integer killedMinions,
        @QueryParam("tripleKills") Integer tripleKills,
        @QueryParam("quadKills") Integer quadKills,
        @QueryParam("pentaKills") Integer pentaKills
) {
    public static PlayerStats valueOf(String ignoreMe) {
        return new PlayerStats(null, 0, 0, 0, 0, 0, 0, 0, null, null, 0.0, 0, 0, 0, 0);
    }
}
