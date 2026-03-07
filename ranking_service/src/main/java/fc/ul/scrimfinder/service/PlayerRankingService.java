package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.request.CreatePlayerRequest;
import fc.ul.scrimfinder.dto.request.UpdateMMRRequest;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.dto.response.PlayerRankingDTO;
import fc.ul.scrimfinder.exception.MMRAlreadyExistsException;
import fc.ul.scrimfinder.exception.PlayerNotFoundException;
import fc.ul.scrimfinder.exception.QueueNotFoundException;
import jakarta.validation.Valid;

import java.util.Optional;

public interface PlayerRankingService {
    PlayerRankingDTO getPlayerRanking(Long playerId, Optional<Long> queueId) throws PlayerNotFoundException, QueueNotFoundException;

    PlayerRankingDTO updatePlayerMMR(Long playerId, UpdateMMRRequest updateMMRRequest) throws PlayerNotFoundException, QueueNotFoundException;

    PaginatedResponseDTO<PlayerRankingDTO> getQueueLeaderboard(int page, int size, Optional<Long> queueId);

    PlayerRankingDTO populatePlayerMMR(Long playerId, @Valid CreatePlayerRequest createPlayerRequest) throws MMRAlreadyExistsException, QueueNotFoundException;
}
