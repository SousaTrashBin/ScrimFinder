package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.domain.PlayerMatchStats;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PlayerMatchStatsRepository implements PanacheRepository<PlayerMatchStats> {}
