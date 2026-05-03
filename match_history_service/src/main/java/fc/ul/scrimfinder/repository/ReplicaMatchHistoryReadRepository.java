package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.dto.request.PlayerStatsDTO;
import fc.ul.scrimfinder.dto.request.TeamStatsDTO;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.util.Champion;
import fc.ul.scrimfinder.util.RiotId;
import fc.ul.scrimfinder.util.Role;
import fc.ul.scrimfinder.util.TeamSide;
import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ReplicaMatchHistoryReadRepository {

    @Inject
    @DataSource("replica")
    AgroalDataSource replicaDataSource;

    public Optional<MatchDTO> findByRiotMatchId(String riotMatchId) throws SQLException {
        String sql =
                """
                SELECT id, riot_match_id, queue_id, patch, game_creation, game_duration,
                       blue_team_side, blue_team_kills, blue_team_deaths, blue_team_assists, blue_team_healing,
                       red_team_side, red_team_kills, red_team_deaths, red_team_assists, red_team_healing
                FROM "match"
                WHERE riot_match_id = ? AND deleted = false
                """;
        try (var conn = replicaDataSource.getConnection();
                var ps = conn.prepareStatement(sql)) {
            ps.setString(1, riotMatchId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapMatch(conn, rs));
            }
        }
    }

    public PaginatedResponseDTO<MatchDTO> findAllPaged(int page, int size) throws SQLException {
        long totalElements;
        try (var conn = replicaDataSource.getConnection();
                var countPs = conn.prepareStatement("SELECT COUNT(*) FROM \"match\" WHERE deleted = false")) {
            try (ResultSet countRs = countPs.executeQuery()) {
                countRs.next();
                totalElements = countRs.getLong(1);
            }

            String sql =
                    """
                    SELECT id, riot_match_id, queue_id, patch, game_creation, game_duration,
                           blue_team_side, blue_team_kills, blue_team_deaths, blue_team_assists, blue_team_healing,
                           red_team_side, red_team_kills, red_team_deaths, red_team_assists, red_team_healing
                    FROM "match"
                    WHERE deleted = false
                    ORDER BY game_creation DESC
                    LIMIT ? OFFSET ?
                    """;
            try (var ps = conn.prepareStatement(sql)) {
                ps.setInt(1, size);
                ps.setInt(2, page * size);
                try (ResultSet rs = ps.executeQuery()) {
                    List<MatchDTO> data = new ArrayList<>();
                    while (rs.next()) {
                        data.add(mapMatch(conn, rs));
                    }
                    int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
                    return new PaginatedResponseDTO<>(data, page, totalPages, totalElements);
                }
            }
        }
    }

    private MatchDTO mapMatch(java.sql.Connection conn, ResultSet rs) throws SQLException {
        MatchDTO dto = new MatchDTO();
        UUID matchId = rs.getObject("id", UUID.class);
        dto.setRiotMatchId(rs.getString("riot_match_id"));
        dto.setQueueId(rs.getObject("queue_id", UUID.class));
        dto.setPatch(rs.getString("patch"));
        dto.setGameCreation(rs.getLong("game_creation"));
        dto.setGameDuration(rs.getLong("game_duration"));
        dto.setTeams(mapTeams(rs));
        dto.setPlayers(findPlayersByMatchId(conn, matchId));
        return dto;
    }

    private List<TeamStatsDTO> mapTeams(ResultSet rs) throws SQLException {
        TeamStatsDTO blue =
                new TeamStatsDTO(
                        mapTeamSide(rs.getInt("blue_team_side")),
                        rs.getInt("blue_team_kills"),
                        rs.getInt("blue_team_deaths"),
                        rs.getInt("blue_team_assists"),
                        rs.getInt("blue_team_healing"));
        TeamStatsDTO red =
                new TeamStatsDTO(
                        mapTeamSide(rs.getInt("red_team_side")),
                        rs.getInt("red_team_kills"),
                        rs.getInt("red_team_deaths"),
                        rs.getInt("red_team_assists"),
                        rs.getInt("red_team_healing"));
        return List.of(blue, red);
    }

    private List<PlayerStatsDTO> findPlayersByMatchId(java.sql.Connection conn, UUID matchId)
            throws SQLException {
        String sql =
                """
                SELECT p.puuid, p.name, p.tag,
                       pm.summoner_icon, pm.summoner_level, pm.kills, pm.deaths, pm.assists,
                       pm.healing, pm.damage_to_players, pm.wards, pm.gold, pm.role, pm.champion,
                       pm.cs_per_minute, pm.killed_minions, pm.triple_kills, pm.quad_kills, pm.penta_kills,
                       pm.side, pm.won, pm.mmr_delta
                FROM player_match_stats pm
                JOIN player p ON p.id = pm.player_id
                WHERE pm.match_id = ? AND pm.deleted = false AND p.deleted = false
                ORDER BY p.name ASC
                """;
        try (var ps = conn.prepareStatement(sql)) {
            ps.setObject(1, matchId);
            try (ResultSet rs = ps.executeQuery()) {
                List<PlayerStatsDTO> players = new ArrayList<>();
                while (rs.next()) {
                    players.add(mapPlayerStats(rs));
                }
                return players;
            }
        }
    }

    private PlayerStatsDTO mapPlayerStats(ResultSet rs) throws SQLException {
        RiotId riotId =
                new RiotId(
                        rs.getString("puuid"),
                        rs.getString("name"),
                        rs.getString("tag"),
                        rs.getInt("summoner_icon"),
                        rs.getInt("summoner_level"));
        return new PlayerStatsDTO(
                riotId,
                rs.getInt("kills"),
                rs.getInt("deaths"),
                rs.getInt("assists"),
                rs.getInt("healing"),
                rs.getInt("damage_to_players"),
                rs.getInt("wards"),
                rs.getInt("gold"),
                mapRole(rs.getInt("role")),
                Champion.fromChampionName(rs.getString("champion")),
                rs.getDouble("cs_per_minute"),
                rs.getInt("killed_minions"),
                rs.getInt("triple_kills"),
                rs.getInt("quad_kills"),
                rs.getInt("penta_kills"),
                mapTeamSide(rs.getInt("side")),
                rs.getBoolean("won"),
                (Integer) rs.getObject("mmr_delta"));
    }

    private Role mapRole(int ordinal) {
        Role[] values = Role.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : Role.NONE;
    }

    private TeamSide mapTeamSide(int ordinal) {
        TeamSide[] values = TeamSide.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : TeamSide.BLUE;
    }
}
