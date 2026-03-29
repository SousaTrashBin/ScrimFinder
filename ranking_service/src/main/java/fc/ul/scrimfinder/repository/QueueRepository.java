package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.domain.QueueEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
public class QueueRepository implements PanacheRepositoryBase<QueueEntity, UUID> {}
