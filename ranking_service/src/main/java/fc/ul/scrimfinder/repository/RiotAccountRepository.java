package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.domain.RiotAccount;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class RiotAccountRepository implements PanacheRepositoryBase<RiotAccount, UUID> {
    public Optional<RiotAccount> findByPuuidOptional(String puuid) {
        return find("puuid", puuid).firstResultOptional();
    }
}
