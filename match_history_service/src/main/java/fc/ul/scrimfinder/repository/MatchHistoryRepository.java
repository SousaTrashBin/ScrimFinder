package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.domain.Match;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MatchHistoryRepository implements PanacheRepository<Match> {}
