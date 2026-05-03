package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.domain.TrainingSyncOutboxEvent;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TrainingSyncOutboxRepository
        implements PanacheRepositoryBase<TrainingSyncOutboxEvent, UUID> {

    public List<TrainingSyncOutboxEvent> findPendingEvents(LocalDateTime now, int limit) {
        return find(
                        "status = ?1 and (nextAttemptAt is null or nextAttemptAt <= ?2) order by createdAt asc",
                        "PENDING",
                        now)
                .page(0, limit)
                .list();
    }
}
