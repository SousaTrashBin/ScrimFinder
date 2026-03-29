package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.domain.MatchTicket;
import fc.ul.scrimfinder.util.TicketStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class MatchTicketRepository implements PanacheRepositoryBase<MatchTicket, UUID> {
    public List<MatchTicket> findActiveTicketsByQueue(UUID queueId) {
        return list(
                "queue.id = ?1 and status = ?2 order by createdAt asc", queueId, TicketStatus.IN_QUEUE);
    }
}
