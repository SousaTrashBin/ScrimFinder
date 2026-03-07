package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.domain.QueueEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class QueueRepository implements PanacheRepository<QueueEntity> {
}
