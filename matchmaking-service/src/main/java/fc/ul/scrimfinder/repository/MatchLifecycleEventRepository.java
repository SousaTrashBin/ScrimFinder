package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.domain.MatchLifecycleEvent;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
public class MatchLifecycleEventRepository
        implements PanacheRepositoryBase<MatchLifecycleEvent, UUID> {}
