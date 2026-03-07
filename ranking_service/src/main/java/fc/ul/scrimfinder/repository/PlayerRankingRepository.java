package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.domain.PlayerRanking;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;


@ApplicationScoped
public class PlayerRankingRepository implements PanacheRepository<PlayerRanking> {
}
