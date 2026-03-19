package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.domain.RiotAccount;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RiotAccountRepository implements PanacheRepository<RiotAccount> {}
