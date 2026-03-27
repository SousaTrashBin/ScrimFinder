package fc.ul.scrimfinder.util;

import fc.ul.scrimfinder.dto.request.filtering.MatchFilters;
import fc.ul.scrimfinder.dto.request.filtering.PlayerFilters;
import fc.ul.scrimfinder.dto.request.filtering.TeamFilters;
import fc.ul.scrimfinder.exception.InvalidIntervalException;
import fc.ul.scrimfinder.exception.InvalidPaginationParametersException;
import fc.ul.scrimfinder.util.interval.Interval;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public class FilterValidator {

    public static void validateInput(int page, int size, MatchFilters filterParams) {
        if (filterParams == null) {
            return;
        }
        validatePagination(page, size);
        validateInterval(filterParams.getPatch(), "patch");
        validateInterval(filterParams.getTime(), "time");
        validatePlayers(filterParams.getPlayers());
        validateTeams(filterParams.getTeams());
    }

    private static void validatePagination(int page, int size) {
        if (page < 0) {
            throw new InvalidPaginationParametersException(
                    "The result page must be bigger or equal to 0");
        }
        if (!(1 <= size && size <= 100)) {
            throw new InvalidPaginationParametersException("The page size must be between 1 and 100");
        }
    }

    private static void validatePlayers(List<PlayerFilters> players) {
        if (players == null || players.isEmpty()) {
            return;
        }
        players.forEach(
                player -> {
                    validateInterval(player.getKills(), "kills");
                    validateInterval(player.getDeaths(), "deaths");
                    validateInterval(player.getAssists(), "assists");
                    validateInterval(player.getHealing(), "healing");
                    validateInterval(player.getDamageToPlayers(), "damage to players");
                    validateInterval(player.getWards(), "wards");
                    validateInterval(player.getGold(), "gold");
                    validateInterval(player.getCsPerMinute(), "cs per minute");
                    validateInterval(player.getKilledMinions(), "killed minions");
                    validateInterval(player.getTripleKills(), "triple kills");
                    validateInterval(player.getQuadKills(), "quad kills");
                    validateInterval(player.getPentaKills(), "penta kills");
                    validateInterval(player.getMmrDelta(), "MMR delta");
                });
    }

    private static void validateTeams(List<TeamFilters> teams) {
        if (teams == null || teams.isEmpty()) {
            return;
        }
        teams.forEach(
                team -> {
                    validateInterval(team.getTeamKills(), "teamKills");
                    validateInterval(team.getTeamDeaths(), "teamDeaths");
                    validateInterval(team.getTeamAssists(), "teamAssists");
                    validateInterval(team.getTeamHealing(), "teamHealing");
                });
    }

    private static void validateInterval(Interval<?> interval, String name) {
        if (interval == null || interval.getMin() == null || interval.getMax() == null) {
            return;
        }
        try {
            Method method =
                    interval.getMin().getClass().getMethod("compareTo", interval.getMax().getClass());
            int comp = (int) method.invoke(interval.getMin(), interval.getMax());
            if (comp > 0) {
                throw new InvalidIntervalException(
                        String.format(
                                "The minimum %s (%s) must be lower than the maximum %s (%s)",
                                name, interval.getMin(), name, interval.getMax()));
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException x) {
            throw new InvalidIntervalException(
                    String.format("Invalid comparison type for %s. Instances cannot be compared", name));
        }
    }
}
