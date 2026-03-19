package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.domain.Player;
import fc.ul.scrimfinder.domain.PlayerRanking;
import fc.ul.scrimfinder.domain.QueueEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class PlayerRankingRepository implements PanacheRepository<PlayerRanking> {
    public Optional<PlayerRanking> findByPlayerAndQueue(Player player, QueueEntity queue) {
        return find("player = ?1 and queue = ?2", player, queue).firstResultOptional();
    }
}
