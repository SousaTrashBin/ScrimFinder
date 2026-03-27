package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.domain.Player;
import fc.ul.scrimfinder.domain.PlayerMatchStats;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PlayerRepository implements PanacheRepository<Player> {

    public void persistIfNotExists(Player player) {
        if (findByPuuid(player.getPuuid()).isEmpty()) {
            persist(player);
        }
    }

    public Optional<Player> findByPuuid(String puuid) {
        return find("puuid = ?1", puuid).firstResultOptional();
    }

    public Optional<Player> findByNameAndTag(String name, String tag) {
        return find("name = ?1 and tag = ?2", name, tag).firstResultOptional();
    }

    public void updatePlayerMatchStats(Long playerId, List<PlayerMatchStats> playerMatchStats) {
        update("playerMatchStats = ?1 where id = ?2", playerMatchStats, playerId);
    }
}
