package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.domain.PlayerRanking;
import fc.ul.scrimfinder.domain.QueueEntity;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.dto.response.PlayerRankingDTO;
import fc.ul.scrimfinder.mapper.PlayerRankingMapper;
import fc.ul.scrimfinder.util.Region;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class ReadOnlyPlayerRankingRepository implements PanacheRepository<PlayerRanking> {

    @Inject PlayerRankingMapper mapper;

    public List<PlayerRanking> findByPlayerId(UUID playerId) {
        return find("player.id", playerId).list();
    }

    public List<PlayerRanking> findByPlayerAndQueue(UUID playerId, UUID queueId) {
        return find("player.id = ?1 and queue.id = ?2", playerId, queueId).list();
    }

    public PaginatedResponseDTO<PlayerRankingDTO> findLeaderboard(
            int page, int size, Optional<QueueEntity> queue, Optional<Region> region) {

        PanacheQuery<PlayerRanking> panacheQuery;
        if (queue.isPresent()) {
            panacheQuery = find("queue = ?1 order by mmr desc", queue.get());
        } else {
            panacheQuery = find("order by mmr desc");
        }

        panacheQuery.page(Page.of(page, size));

        var list = panacheQuery.list().stream().map(mapper::toDTO).collect(Collectors.toList());

        return new PaginatedResponseDTO<>(list, page, panacheQuery.pageCount(), panacheQuery.count());
    }
}
