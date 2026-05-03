package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.dto.response.LobbyDTO;
import fc.ul.scrimfinder.dto.response.MatchTicketDTO;
import fc.ul.scrimfinder.dto.response.QueueDTO;
import fc.ul.scrimfinder.util.MatchmakingMode;
import fc.ul.scrimfinder.util.Region;
import fc.ul.scrimfinder.util.Role;
import fc.ul.scrimfinder.util.TicketStatus;
import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ReplicaMatchmakingReadRepository {
    @Inject
    @DataSource("replica")
    AgroalDataSource replicaDataSource;

    public Optional<QueueDTO> findQueueById(UUID id) throws SQLException {
        String sql =
                """
                SELECT id, name, region, namespace, required_players, is_role_queue, mode, mmr_window
                FROM queue
                WHERE id = ?
                """;
        try (var conn = replicaDataSource.getConnection();
                var ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                QueueDTO dto = new QueueDTO();
                dto.setId(rs.getObject("id", UUID.class));
                dto.setName(rs.getString("name"));
                String region = rs.getString("region");
                dto.setRegion(region == null ? null : Region.valueOf(region));
                dto.setNamespace(rs.getString("namespace"));
                dto.setRequiredPlayers(rs.getInt("required_players"));
                dto.setRoleQueue(rs.getBoolean("is_role_queue"));
                dto.setMode(MatchmakingMode.valueOf(rs.getString("mode")));
                dto.setMmrWindow(rs.getInt("mmr_window"));
                return Optional.of(dto);
            }
        }
    }

    public boolean playerExists(UUID playerId) throws SQLException {
        try (var conn = replicaDataSource.getConnection();
                var ps = conn.prepareStatement("SELECT 1 FROM player WHERE id = ?")) {
            ps.setObject(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Optional<LobbyDTO> findLobbyByTicketId(UUID ticketId) throws SQLException {
        String sql =
                """
                SELECT l.id, l.queue_id, l.region, l.created_at, m.id AS match_id
                FROM match_ticket t
                JOIN lobby l ON l.id = t.lobby_id
                LEFT JOIN "match" m ON m.lobby_id = l.id
                WHERE t.id = ?
                """;
        try (var conn = replicaDataSource.getConnection();
                var ps = conn.prepareStatement(sql)) {
            ps.setObject(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                LobbyDTO lobby = mapLobbyRow(rs);
                lobby.setTickets(findTicketsByLobbyId(lobby.getId()));
                return Optional.of(lobby);
            }
        }
    }

    public List<MatchTicketDTO> findTicketsByPlayerId(UUID playerId) throws SQLException {
        String sql =
                """
                SELECT id, player_id, queue_id, region, role, status, mmr, created_at, lobby_id
                FROM match_ticket
                WHERE player_id = ?
                ORDER BY created_at DESC
                """;
        try (var conn = replicaDataSource.getConnection();
                var ps = conn.prepareStatement(sql)) {
            ps.setObject(1, playerId);
            return mapTickets(ps.executeQuery());
        }
    }

    public List<LobbyDTO> findLobbiesByPlayerId(UUID playerId) throws SQLException {
        String sql =
                """
                SELECT DISTINCT l.id, l.queue_id, l.region, l.created_at, m.id AS match_id
                FROM match_ticket t
                JOIN lobby l ON l.id = t.lobby_id
                LEFT JOIN "match" m ON m.lobby_id = l.id
                WHERE t.player_id = ?
                ORDER BY l.created_at DESC
                """;
        try (var conn = replicaDataSource.getConnection();
                var ps = conn.prepareStatement(sql)) {
            ps.setObject(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                Map<UUID, LobbyDTO> lobbies = new LinkedHashMap<>();
                while (rs.next()) {
                    LobbyDTO lobby = mapLobbyRow(rs);
                    lobbies.putIfAbsent(lobby.getId(), lobby);
                }
                for (LobbyDTO lobby : lobbies.values()) {
                    lobby.setTickets(findTicketsByLobbyId(lobby.getId()));
                }
                return new ArrayList<>(lobbies.values());
            }
        }
    }

    private List<MatchTicketDTO> findTicketsByLobbyId(UUID lobbyId) throws SQLException {
        String sql =
                """
                SELECT id, player_id, queue_id, region, role, status, mmr, created_at, lobby_id
                FROM match_ticket
                WHERE lobby_id = ?
                ORDER BY created_at ASC
                """;
        try (var conn = replicaDataSource.getConnection();
                var ps = conn.prepareStatement(sql)) {
            ps.setObject(1, lobbyId);
            return mapTickets(ps.executeQuery());
        }
    }

    private List<MatchTicketDTO> mapTickets(ResultSet rs) throws SQLException {
        List<MatchTicketDTO> tickets = new ArrayList<>();
        while (rs.next()) {
            MatchTicketDTO dto = new MatchTicketDTO();
            dto.setId(rs.getObject("id", UUID.class));
            dto.setPlayerId(rs.getObject("player_id", UUID.class));
            dto.setQueueId(rs.getObject("queue_id", UUID.class));
            dto.setRegion(Region.valueOf(rs.getString("region")));
            dto.setRole(Role.valueOf(rs.getString("role")));
            dto.setStatus(TicketStatus.valueOf(rs.getString("status")));
            dto.setMmr(rs.getInt("mmr"));
            dto.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            dto.setLobbyId(rs.getObject("lobby_id", UUID.class));
            tickets.add(dto);
        }
        return tickets;
    }

    private LobbyDTO mapLobbyRow(ResultSet rs) throws SQLException {
        LobbyDTO lobby = new LobbyDTO();
        lobby.setId(rs.getObject("id", UUID.class));
        lobby.setQueueId(rs.getObject("queue_id", UUID.class));
        lobby.setRegion(Region.valueOf(rs.getString("region")));
        lobby.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        lobby.setMatchId(rs.getObject("match_id", UUID.class));
        return lobby;
    }
}
