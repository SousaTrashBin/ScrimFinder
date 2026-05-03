package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.domain.Match;
import fc.ul.scrimfinder.dto.request.filtering.MatchFilters;
import fc.ul.scrimfinder.dto.request.filtering.PlayerFilters;
import fc.ul.scrimfinder.dto.request.filtering.TeamFilters;
import fc.ul.scrimfinder.dto.request.sorting.SortParams;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.util.Champion;
import fc.ul.scrimfinder.util.SortColumn;
import fc.ul.scrimfinder.util.SortDirection;
import fc.ul.scrimfinder.util.TeamSide;
import fc.ul.scrimfinder.util.interval.Interval;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;

@ApplicationScoped
public class ReadOnlyMatchHistorySearchRepository implements PanacheRepository<Match> {

    public Optional<Match> findByRiotMatchId(String riotMatchId) {
        return find("riotMatchId", riotMatchId).firstResultOptional();
    }

    public boolean deleteByRiotMatchId(String riotMatchId) {
        return delete("riotMatchId", riotMatchId) > 0;
    }

    public PaginatedResponseDTO<Match> search(int page, int size, MatchFilters filterParams) {
        StringBuilder queryBuilder = new StringBuilder();
        Map<String, Object> parameters = new HashMap<>();

        if (filterParams == null) {
            return paginatedResult(page, size, queryBuilder, parameters);
        }

        boolean withPrefixAnd = false;

        withPrefixAnd =
                addEqualCondition(
                        "queueId",
                        filterParams.getQueueId(),
                        "queueId",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
        withPrefixAnd =
                addChampionsCondition(filterParams.getChampions(), withPrefixAnd, queryBuilder, parameters);
        withPrefixAnd =
                addIntervalCondition(
                        "patch", filterParams.getPatch(), "patch", withPrefixAnd, queryBuilder, parameters);
        withPrefixAnd =
                addTimeCondition(
                        "time",
                        filterParams.getTime(),
                        "gameCreation",
                        "gameCreation + gameDuration",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);

        int currPlayerFilter = 0;
        List<PlayerFilters> playerFilters = filterParams.getPlayers();
        if (!(playerFilters == null || playerFilters.isEmpty())) {
            for (PlayerFilters pf : playerFilters) {
                withPrefixAnd = appendAndIfNeeded(withPrefixAnd, queryBuilder);
                queryBuilder.append(Clause.playerMatchStatsJoinClause());
                addPlayerFilterConditions(
                        pf, String.valueOf(currPlayerFilter), false, queryBuilder, parameters);
                currPlayerFilter++;
                queryBuilder.append(")");
            }
        }

        int currTeamFilter = 0;
        List<TeamFilters> teamFilters = filterParams.getTeams();
        if (teamFilters != null) {
            for (TeamFilters tf : teamFilters) {
                if (tf.getTeamSide() == null) {
                    withPrefixAnd = appendAndIfNeeded(withPrefixAnd, queryBuilder);
                    queryBuilder.append("(");
                    addTeamFilterConditions(
                            tf, "blue.", String.valueOf(currTeamFilter), false, queryBuilder, parameters);
                    queryBuilder.append("OR ");
                    addTeamFilterConditions(
                            tf, "red.", String.valueOf(currTeamFilter), false, queryBuilder, parameters);
                    queryBuilder.append(")");
                } else if (tf.getTeamSide().equals(TeamSide.BLUE)) {
                    addTeamFilterConditions(
                            tf, "blue.", String.valueOf(currTeamFilter), withPrefixAnd, queryBuilder, parameters);
                } else if (tf.getTeamSide().equals(TeamSide.RED)) {
                    addTeamFilterConditions(
                            tf, "red.", String.valueOf(currTeamFilter), withPrefixAnd, queryBuilder, parameters);
                }
                currTeamFilter++;
            }
        }

        sortResult(filterParams.getSortParams(), queryBuilder);

        return paginatedResult(page, size, queryBuilder, parameters);
    }

    private void addPlayerFilterConditions(
            PlayerFilters playerFilters,
            String suffix,
            boolean withPrefixAnd,
            StringBuilder queryBuilder,
            Map<String, Object> parameters) {
        withPrefixAnd =
                addEqualCondition(
                        "puuid" + suffix,
                        playerFilters.getPuuid(),
                        "p.puuid",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
        withPrefixAnd =
                addEqualCondition(
                        "playerName" + suffix,
                        playerFilters.getPlayerName(),
                        "p.name",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
        withPrefixAnd =
                addEqualCondition(
                        "playerTag" + suffix,
                        playerFilters.getPlayerTag(),
                        "p.tag",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
        withPrefixAnd =
                addEqualCondition(
                        "summonerIcon" + suffix,
                        playerFilters.getSummonerIcon(),
                        "pm.summonerIcon",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
        withPrefixAnd =
                addIntervalCondition(
                        "summonerLevel" + suffix,
                        playerFilters.getSummonerLevel(),
                        "pm.summonerLevel",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
        withPrefixAnd =
                addIntervalCondition(
                        "kills" + suffix,
                        playerFilters.getKills(),
                        "pm.kills",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
        withPrefixAnd =
                addIntervalCondition(
                        "deaths" + suffix,
                        playerFilters.getDeaths(),
                        "pm.deaths",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
        withPrefixAnd =
                addIntervalCondition(
                        "assists" + suffix,
                        playerFilters.getAssists(),
                        "pm.assists",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
        withPrefixAnd =
                addIntervalCondition(
                        "healing" + suffix,
                        playerFilters.getHealing(),
                        "pm.healing",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
        withPrefixAnd =
                addIntervalCondition(
                        "damageToPlayers" + suffix,
                        playerFilters.getDamageToPlayers(),
                        "pm.damageToPlayers",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
        withPrefixAnd =
                addIntervalCondition(
                        "wards" + suffix,
                        playerFilters.getWards(),
                        "pm.wards",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
        withPrefixAnd =
                addIntervalCondition(
                        "gold" + suffix,
                        playerFilters.getGold(),
                        "pm.gold",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
        withPrefixAnd =
                addEqualCondition(
                        "role" + suffix,
                        playerFilters.getRole(),
                        "pm.role",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
        withPrefixAnd =
                addCollectionCondition(
                        "champions" + suffix,
                        playerFilters.getChampions(),
                        "pm.champion",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
        withPrefixAnd =
                addIntervalCondition(
                        "csPerMinute" + suffix,
                        playerFilters.getCsPerMinute(),
                        "pm.csPerMinute",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
        withPrefixAnd =
                addIntervalCondition(
                        "killedMinions" + suffix,
                        playerFilters.getKilledMinions(),
                        "pm.killedMinions",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
        withPrefixAnd =
                addIntervalCondition(
                        "tripleKills" + suffix,
                        playerFilters.getTripleKills(),
                        "pm.tripleKills",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
        withPrefixAnd =
                addIntervalCondition(
                        "quadKills" + suffix,
                        playerFilters.getQuadKills(),
                        "pm.quadKills",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
        withPrefixAnd =
                addIntervalCondition(
                        "pentaKills" + suffix,
                        playerFilters.getPentaKills(),
                        "pm.pentaKills",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
        withPrefixAnd =
                addEqualCondition(
                        "side" + suffix,
                        playerFilters.getTeamSide(),
                        "pm.teamSide",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
        withPrefixAnd =
                addEqualCondition(
                        "won" + suffix,
                        playerFilters.getWon(),
                        "pm.won",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
        withPrefixAnd =
                addIntervalCondition(
                        "mmrDelta" + suffix,
                        playerFilters.getMmrDelta(),
                        "pm.mmrDelta",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
    }

    private void addTeamFilterConditions(
            TeamFilters teamFilters,
            String prefix,
            String suffix,
            boolean withPrefixAnd,
            StringBuilder queryBuilder,
            Map<String, Object> parameters) {
        withPrefixAnd =
                addIntervalCondition(
                        "teamKills" + suffix,
                        teamFilters.getTeamKills(),
                        prefix + "teamKills",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
        withPrefixAnd =
                addIntervalCondition(
                        "teamDeaths" + suffix,
                        teamFilters.getTeamDeaths(),
                        prefix + "teamDeaths",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
        withPrefixAnd =
                addIntervalCondition(
                        "teamAssists" + suffix,
                        teamFilters.getTeamAssists(),
                        prefix + "teamAssists",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
        withPrefixAnd =
                addIntervalCondition(
                        "teamHealing" + suffix,
                        teamFilters.getTeamHealing(),
                        prefix + "teamHealing",
                        withPrefixAnd,
                        queryBuilder,
                        parameters);
    }

    private boolean addEqualCondition(
            String paramName,
            Object value,
            String field,
            boolean withPrefixAnd,
            StringBuilder queryBuilder,
            Map<String, Object> parameters) {
        if (value == null) {
            return withPrefixAnd;
        }
        withPrefixAnd = appendAndIfNeeded(withPrefixAnd, queryBuilder);
        queryBuilder.append(Clause.equalClause(field, paramName)).append(" ");
        parameters.put(paramName, value);
        return withPrefixAnd;
    }

    private boolean addChampionsCondition(
            Object value,
            boolean withPrefixAnd,
            StringBuilder queryBuilder,
            Map<String, Object> parameters) {
        if (!(value instanceof List && !((List<?>) value).isEmpty())) {
            return withPrefixAnd;
        }
        for (Champion champion : (List<Champion>) value) {
            String championName = champion.name();
            withPrefixAnd = appendAndIfNeeded(withPrefixAnd, queryBuilder);
            queryBuilder.append(Clause.championClause(championName)).append(" ");
            parameters.put(championName, champion);
        }
        return withPrefixAnd;
    }

    private boolean addTimeCondition(
            String paramName,
            Interval<?> interval,
            String fieldStart,
            String fieldEnd,
            boolean withPrefixAnd,
            StringBuilder queryBuilder,
            Map<String, Object> parameters) {
        if (interval == null) {
            return withPrefixAnd;
        }
        if (interval.getMin() != null) {
            String minParamName = paramName + "min";
            withPrefixAnd = appendAndIfNeeded(withPrefixAnd, queryBuilder);
            queryBuilder
                    .append("(")
                    .append(Clause.minClause(minParamName, fieldEnd))
                    .append(" OR ")
                    .append(Clause.betweenClause(minParamName, fieldStart, fieldEnd))
                    .append(") ");
            parameters.put(minParamName, interval.getMin());
        }
        if (interval.getMax() != null) {
            String maxParamName = paramName + "max";
            withPrefixAnd = appendAndIfNeeded(withPrefixAnd, queryBuilder);
            queryBuilder
                    .append("(")
                    .append(Clause.maxClause(fieldStart, maxParamName))
                    .append(" OR ")
                    .append(Clause.betweenClause(maxParamName, fieldStart, fieldEnd))
                    .append(") ");
            parameters.put(maxParamName, interval.getMax());
        }
        return withPrefixAnd;
    }

    private boolean addIntervalCondition(
            String paramName,
            Interval<?> interval,
            String field,
            boolean withPrefixAnd,
            StringBuilder queryBuilder,
            Map<String, Object> parameters) {
        if (interval == null) {
            return withPrefixAnd;
        }
        if (interval.getMin() != null) {
            withPrefixAnd = appendAndIfNeeded(withPrefixAnd, queryBuilder);
            String minParamName = paramName + "min";
            queryBuilder.append(Clause.minClause(minParamName, field)).append(" ");
            parameters.put(minParamName, interval.getMin());
        }
        if (interval.getMax() != null) {
            withPrefixAnd = appendAndIfNeeded(withPrefixAnd, queryBuilder);
            String maxParamName = paramName + "max";
            queryBuilder.append(Clause.maxClause(field, maxParamName)).append(" ");
            parameters.put(maxParamName, interval.getMax());
        }
        return withPrefixAnd;
    }

    private boolean addCollectionCondition(
            String paramName,
            Object value,
            String field,
            Boolean withPrefixAnd,
            StringBuilder queryBuilder,
            Map<String, Object> parameters) {
        if (!(value instanceof Collection && !((Collection<?>) value).isEmpty())) {
            return withPrefixAnd;
        }
        withPrefixAnd = appendAndIfNeeded(withPrefixAnd, queryBuilder);
        queryBuilder.append(Clause.inCollectionClause(field, paramName)).append(" ");
        parameters.put(paramName, value);
        return withPrefixAnd;
    }

    private boolean appendAndIfNeeded(boolean withPrefixAnd, StringBuilder queryBuilder) {
        if (withPrefixAnd) {
            queryBuilder.append("AND ");
        } else {
            withPrefixAnd = true;
        }
        return withPrefixAnd;
    }

    private void sortResult(List<SortParams> sortParams, StringBuilder queryBuilder) {
        if (sortParams == null || sortParams.isEmpty()) {
            return;
        }
        SortParams[] sortParamsArray = sortParams.toArray(new SortParams[0]);
        queryBuilder.append(Clause.orderByClause());
        addSortingColumn(sortParamsArray[0], queryBuilder);
        for (int i = 1; i < sortParamsArray.length; i++) {
            queryBuilder.append(", ");
            addSortingColumn(sortParamsArray[i], queryBuilder);
        }
    }

    private void addSortingColumn(SortParams sortParam, StringBuilder queryBuilder) {
        if (sortParam == null || sortParam.column() == null) {
            return;
        }
        SortColumn column = sortParam.column();
        SortDirection direction = sortParam.direction();
        if (direction == null) {
            direction = SortDirection.ASC;
        }
        queryBuilder.append(Clause.sortClause(column.getFieldName(), direction.getDirection()));
    }

    private PaginatedResponseDTO<Match> paginatedResult(
            int page, int size, StringBuilder queryBuilder, Map<String, Object> parameters) {
        String conditions = queryBuilder.isEmpty() ? "1=1" : queryBuilder.toString();

        long totalElements = find(conditions, parameters).count();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        List<Match> matches = find(conditions, parameters).page(Page.of(page, size)).list();

        return new PaginatedResponseDTO<>(matches, page, totalPages, totalElements);
    }
}
