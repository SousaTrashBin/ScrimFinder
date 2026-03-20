package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.domain.MatchTicket;
import fc.ul.scrimfinder.util.TicketStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class MatchTicketRepository implements PanacheRepository<MatchTicket> {
    public List<MatchTicket> findActiveTicketsByQueue(Long queueId) {
        return list(
                "queue.id = ?1 and status = ?2 order by createdAt asc", queueId, TicketStatus.IN_QUEUE);
    }
}
