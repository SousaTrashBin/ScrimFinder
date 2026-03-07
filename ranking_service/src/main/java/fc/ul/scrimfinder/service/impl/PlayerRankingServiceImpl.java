package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.domain.PlayerRanking;
import fc.ul.scrimfinder.dto.request.CreatePlayerRequest;
import fc.ul.scrimfinder.dto.request.UpdateMMRRequest;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.dto.response.PlayerRankingDTO;
import fc.ul.scrimfinder.mapper.PlayerRankingMapper;
import fc.ul.scrimfinder.repository.PlayerRankingRepository;
import fc.ul.scrimfinder.service.PlayerRankingService;
import fc.ul.scrimfinder.service.QueueService;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

@ApplicationScoped
public class PlayerRankingServiceImpl implements PlayerRankingService {
    @Inject
    PlayerRankingRepository playerRankingRepository;

    @Inject
    PlayerRankingMapper playerRankingMapper;

    @Inject
    QueueService queueService;

    @Override
    public PlayerRankingDTO getPlayerRanking(Long playerId, Optional<Long> queueId) {
        return null;
    }

    @Override
    public PlayerRankingDTO updatePlayerMMR(Long playerId, UpdateMMRRequest updateMMRRequest) {
        return null;
    }

    @Override
    public PaginatedResponseDTO<PlayerRankingDTO> getQueueLeaderboard(int page, int size, Optional<Long> queueId) {
        PanacheQuery<PlayerRanking> query = queueId.isPresent()
                ? playerRankingRepository.find("queryId", queueId.get())
                : playerRankingRepository.findAll();

        return new PaginatedResponseDTO<>(
                query.page(page, size).list().stream().map(playerRankingMapper::toDTO).toList(),
                page,
                query.pageCount(),
                query.count()
        );
    }

    @Override
    public PlayerRankingDTO populatePlayerMMR(Long playerId, CreatePlayerRequest createPlayerRequest) {
        return null;
    }
}
