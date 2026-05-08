package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.domain.MatchResultOutboxEvent;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class MatchResultOutboxRepository
        implements PanacheRepositoryBase<MatchResultOutboxEvent, UUID> {

    public Optional<MatchResultOutboxEvent> findByMatchId(UUID matchId) {
        return find("matchId", matchId).firstResultOptional();
    }

    public List<MatchResultOutboxEvent> findPending(LocalDateTime now, int limit) {
        return find(
                        "status = ?1 and (nextAttemptAt is null or nextAttemptAt <= ?2) order by createdAt asc",
                        "PENDING",
                        now)
                .page(0, limit)
                .list();
    }
}
