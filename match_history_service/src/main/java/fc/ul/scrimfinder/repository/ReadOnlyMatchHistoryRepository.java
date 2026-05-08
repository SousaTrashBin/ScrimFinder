package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.domain.Match;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class ReadOnlyMatchHistoryRepository implements PanacheRepository<Match> {

    public Optional<Match> findByRiotMatchId(String riotMatchId) {
        return find("riotMatchId", riotMatchId).firstResultOptional();
    }
}
