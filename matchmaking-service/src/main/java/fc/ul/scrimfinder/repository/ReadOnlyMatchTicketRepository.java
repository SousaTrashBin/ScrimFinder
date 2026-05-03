package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.domain.MatchTicket;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ReadOnlyMatchTicketRepository implements PanacheRepositoryBase<MatchTicket, UUID> {
    public List<MatchTicket> findByPlayerId(UUID playerId) {
        return list("player.id = ?1 order by createdAt desc", playerId);
    }
}
