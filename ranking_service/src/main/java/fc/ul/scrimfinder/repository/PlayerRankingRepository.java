package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.domain.Player;
import fc.ul.scrimfinder.domain.PlayerRanking;
import fc.ul.scrimfinder.domain.QueueEntity;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.dto.response.PlayerRankingDTO;
import fc.ul.scrimfinder.mapper.PlayerRankingMapper;
import fc.ul.scrimfinder.util.Region;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class PlayerRankingRepository implements PanacheRepositoryBase<PlayerRanking, UUID> {

    @Inject PlayerRankingMapper mapper;

    public Optional<PlayerRanking> findByPlayerAndQueue(Player player, QueueEntity queue) {
        return find("player = ?1 and queue = ?2", player, queue).firstResultOptional();
    }

    public List<PlayerRanking> findByPlayerId(UUID playerId) {
        return list("player.id", playerId);
    }

    public List<PlayerRanking> findByPlayerAndQueue(UUID playerId, UUID queueId) {
        return list("player.id = ?1 and queue.id = ?2", playerId, queueId);
    }

    public PaginatedResponseDTO<PlayerRankingDTO> findLeaderboard(
            int page, int size, Optional<QueueEntity> queue, Optional<Region> region) {

        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (queue.isPresent()) {
            clauses.add("queue = ?" + (params.size() + 1));
            params.add(queue.get());
        }

        if (region.isPresent()) {
            clauses.add(
                    "player.id in (select account.player.id from RiotAccount account where account.isPrimary = true and account.region = ?"
                            + (params.size() + 1)
                            + ")");
            params.add(region.get());
        }

        String query = clauses.isEmpty() ? "" : String.join(" and ", clauses) + " ";
        PanacheQuery<PlayerRanking> panacheQuery = find(query + "order by mmr desc", params.toArray());

        panacheQuery.page(Page.of(page, size));

        var list = panacheQuery.list().stream().map(mapper::toDTO).collect(Collectors.toList());

        return new PaginatedResponseDTO<>(list, page, panacheQuery.pageCount(), panacheQuery.count());
    }
}
