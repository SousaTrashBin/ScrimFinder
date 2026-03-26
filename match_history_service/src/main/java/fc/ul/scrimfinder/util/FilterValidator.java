package fc.ul.scrimfinder.util;

import fc.ul.scrimfinder.dto.request.filtering.MatchFilters;
import fc.ul.scrimfinder.dto.request.filtering.PlayerFilters;
import fc.ul.scrimfinder.dto.request.filtering.TeamFilters;
import fc.ul.scrimfinder.exception.InvalidIntervalException;
import fc.ul.scrimfinder.exception.InvalidPaginationParametersException;
import fc.ul.scrimfinder.exception.InvalidPlayersException;
import fc.ul.scrimfinder.exception.InvalidTeamsException;
import fc.ul.scrimfinder.util.interval.Interval;
import fc.ul.scrimfinder.util.interval.PatchInterval;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public class FilterValidator {

    public static void validateInput(int page, int size, MatchFilters filterParams) {
        validatePagination(page, size);
        validatePatch(filterParams.getPatch());
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

    private static void validatePatch(PatchInterval patch) {
        if (patch == null || patch.getMin() == null || patch.getMax() == null) {
            return;
        }
        String[] minPatchParts = patch.getMin().split("\\.");
        String[] maxPatchParts = patch.getMax().split("\\.");
        int minLength = Math.min(minPatchParts.length, maxPatchParts.length);
        for (int i = 0; i < minLength; i++) {
            int comp = Integer.parseInt(minPatchParts[i]) - Integer.parseInt(maxPatchParts[i]);
            if (comp < 0) {
                return;
            } else if (comp > 0) {
                throw new InvalidIntervalException(
                        String.format(
                                "The minimum patch (%s) must be older than the maximum patch (%s)",
                                patch.getMin(), patch.getMax()));
            }
        }
    }

    private static void validatePlayers(List<PlayerFilters> players) {
        if (players == null || players.isEmpty()) {
            return;
        }
        if (players.size() > 10) {
            throw new InvalidPlayersException("The match must have no more than 10 players");
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
        if (teams.size() > 2) {
            throw new InvalidTeamsException("The match must have no more than 2 teams");
        }
        if (teams.size() == 2) {
            validateTeamSides(teams.getFirst().getSide(), teams.getLast().getSide());
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
                    String.format("Invalid number type for %s. Instances cannot be compared", name));
        }
    }

    private static void validateTeamSides(TeamSide side1, TeamSide side2) {
        if (side1 == null || side2 == null) {
            return;
        }
        if (side1.equals(side2)) {
            throw new InvalidTeamsException(
                    "There cannot be 2 teams in side " + side1 + " for the same match");
        }
    }
}
