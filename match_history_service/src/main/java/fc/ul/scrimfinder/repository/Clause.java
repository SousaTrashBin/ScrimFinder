package fc.ul.scrimfinder.repository;

public class Clause {

    static String equalClause(String field, String param) {
        return String.format("%s = :%s", field, param);
    }

    static String minClause(String param, String field) {
        return String.format(":%s <= %s", param, field);
    }

    static String maxClause(String field, String param) {
        return String.format("%s <= :%s", field, param);
    }

    static String betweenClause(String param, String fieldStart, String fieldEnd) {
        return String.format(":%s BETWEEN %s AND %s", param, fieldStart, fieldEnd);
    }

    static String inCollectionClause(String field, String param) {
        return String.format("%s IN (:%s)", field, param);
    }

    static String championClause(String championParamName) {
        return String.format(
                "id IN (SELECT pm.match.id FROM PlayerMatchStats pm JOIN pm.match m2 WHERE pm.champion = :%s)",
                championParamName);
    }

    static String playerMatchStatsJoinClause() {
        return "id IN (SELECT pm.match.id FROM PlayerMatchStats pm JOIN pm.match m2 JOIN pm.player p WHERE ";
    }

    static String orderByClause() {
        return "ORDER BY ";
    }

    static String sortClause(String column, String direction) {
        return String.format("%s %s", column, direction);
    }
}
