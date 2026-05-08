package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.domain.Queue;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ReadOnlyQueueRepository implements PanacheRepository<Queue> {
    public Optional<Queue> findByIdOptional(UUID id) {
        return find("id", id).firstResultOptional();
    }
}
