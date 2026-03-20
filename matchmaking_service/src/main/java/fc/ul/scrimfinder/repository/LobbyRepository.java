package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.domain.Lobby;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LobbyRepository implements PanacheRepository<Lobby> {}
