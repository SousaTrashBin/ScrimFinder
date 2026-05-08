package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.dto.response.PlayerRankingDTO;
import fc.ul.scrimfinder.util.Region;
import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ReplicaPlayerRankingReadRepository {
    @Inject
    @DataSource("replica")
    AgroalDataSource replicaDataSource;

    public List<PlayerRankingDTO> findByPlayerId(UUID playerId) throws SQLException {
        String sql =
                """
                SELECT pr.id, p.id AS player_id, p.discord_username, ra.puuid, ra.region,
                       pr.queue_id, pr.mmr, pr.wins, pr.losses
                FROM player_ranking pr
                JOIN player p ON p.id = pr.player_id
                LEFT JOIN riot_account ra ON ra.player_id = p.id AND ra.is_primary = TRUE
                WHERE p.id = ?
                ORDER BY pr.mmr DESC
                """;
        try (var conn = replicaDataSource.getConnection();
                var ps = conn.prepareStatement(sql)) {
            ps.setObject(1, playerId);
            return toRankingList(ps.executeQuery());
        }
    }

    public List<PlayerRankingDTO> findByPlayerAndQueue(UUID playerId, UUID queueId) throws SQLException {
        String sql =
                """
                SELECT pr.id, p.id AS player_id, p.discord_username, ra.puuid, ra.region,
                       pr.queue_id, pr.mmr, pr.wins, pr.losses
                FROM player_ranking pr
                JOIN player p ON p.id = pr.player_id
                LEFT JOIN riot_account ra ON ra.player_id = p.id AND ra.is_primary = TRUE
                WHERE p.id = ? AND pr.queue_id = ?
                """;
        try (var conn = replicaDataSource.getConnection();
                var ps = conn.prepareStatement(sql)) {
            ps.setObject(1, playerId);
            ps.setObject(2, queueId);
            return toRankingList(ps.executeQuery());
        }
    }

    public PaginatedResponseDTO<PlayerRankingDTO> findLeaderboard(
            int page, int size, Optional<UUID> queueId, Optional<Region> region) throws SQLException {
        String base =
                """
                FROM player_ranking pr
                JOIN player p ON p.id = pr.player_id
                LEFT JOIN riot_account ra ON ra.player_id = p.id AND ra.is_primary = TRUE
                WHERE (? IS NULL OR pr.queue_id = ?)
                  AND (? IS NULL OR ra.region = ?)
                """;
        String dataSql =
                """
                SELECT pr.id, p.id AS player_id, p.discord_username, ra.puuid, ra.region,
                       pr.queue_id, pr.mmr, pr.wins, pr.losses
                """
                        + base
                        + " ORDER BY pr.mmr DESC OFFSET ? LIMIT ?";
        String countSql = "SELECT COUNT(*) " + base;

        try (var conn = replicaDataSource.getConnection()) {
            long total;
            try (PreparedStatement cps = conn.prepareStatement(countSql)) {
                bindLeaderboardFilters(cps, queueId, region);
                try (ResultSet crs = cps.executeQuery()) {
                    crs.next();
                    total = crs.getLong(1);
                }
            }

            List<PlayerRankingDTO> data;
            try (PreparedStatement dps = conn.prepareStatement(dataSql)) {
                bindLeaderboardFilters(dps, queueId, region);
                dps.setInt(5, page * size);
                dps.setInt(6, size);
                data = toRankingList(dps.executeQuery());
            }

            int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
            return new PaginatedResponseDTO<>(data, page, totalPages, total);
        }
    }

    private void bindLeaderboardFilters(
            PreparedStatement ps, Optional<UUID> queueId, Optional<Region> region) throws SQLException {
        if (queueId.isPresent()) {
            ps.setObject(1, queueId.get());
            ps.setObject(2, queueId.get());
        } else {
            ps.setNull(1, Types.OTHER);
            ps.setNull(2, Types.OTHER);
        }
        if (region.isPresent()) {
            ps.setString(3, region.get().name());
            ps.setString(4, region.get().name());
        } else {
            ps.setNull(3, Types.VARCHAR);
            ps.setNull(4, Types.VARCHAR);
        }
    }

    private List<PlayerRankingDTO> toRankingList(ResultSet rs) throws SQLException {
        List<PlayerRankingDTO> rows = new ArrayList<>();
        while (rs.next()) {
            String regionValue = rs.getString("region");
            rows.add(
                    new PlayerRankingDTO(
                            rs.getObject("id", UUID.class),
                            rs.getObject("player_id", UUID.class),
                            rs.getString("discord_username"),
                            rs.getString("puuid"),
                            regionValue == null ? null : Region.valueOf(regionValue),
                            rs.getObject("queue_id", UUID.class),
                            rs.getInt("mmr"),
                            rs.getInt("wins"),
                            rs.getInt("losses")));
        }
        return rows;
    }
}
