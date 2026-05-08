package fc.ul.scrimfinder.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "match_result_outbox")
@Getter
@Setter
public class MatchResultOutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "match_id", nullable = false, unique = true)
    private UUID matchId;

    @Column(name = "external_game_id", nullable = false)
    private String externalGameId;

    @Column(name = "queue_id", nullable = false)
    private UUID queueId;

    @Column(name = "player_deltas_json", nullable = false)
    private String playerDeltasJson;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
