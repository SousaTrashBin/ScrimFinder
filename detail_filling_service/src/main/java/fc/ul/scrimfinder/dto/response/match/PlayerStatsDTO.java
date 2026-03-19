package fc.ul.scrimfinder.dto.response.match;

import fc.ul.scrimfinder.util.RiotId;
import fc.ul.scrimfinder.util.Role;
import fc.ul.scrimfinder.util.TeamSide;

public record PlayerStatsDTO(
        RiotId riotId,
        Integer kills,
        Integer deaths,
        Integer assists,
        Integer healing,
        Integer damageToPlayers,
        Integer wards,
        Integer gold,
        Role role,
        String champion,
        Integer killedMinions,
        Integer tripleKills,
        Integer quadKills,
        Integer pentaKills,
        TeamSide side,
        Boolean won
) {
}
