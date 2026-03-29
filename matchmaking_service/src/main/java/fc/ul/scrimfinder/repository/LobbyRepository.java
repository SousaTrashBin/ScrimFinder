package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.domain.Lobby;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
public class LobbyRepository implements PanacheRepositoryBase<Lobby, UUID> {}
