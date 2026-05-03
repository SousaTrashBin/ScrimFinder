package fc.ul.scrimfinder.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "match_lifecycle_events")
@Getter
@Setter
public class MatchLifecycleEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(nullable = false)
    private String step;

    @Column(nullable = false)
    private String status;

    @Column(length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
