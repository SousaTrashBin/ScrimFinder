package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.request.CreatePlayerRequest;
import fc.ul.scrimfinder.dto.request.MatchResultRequest;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.dto.response.PlayerRankingDTO;
import fc.ul.scrimfinder.exception.LeagueAccountNotLinkedException;
import fc.ul.scrimfinder.exception.MMRAlreadyExistsException;
import fc.ul.scrimfinder.exception.PlayerNotFoundException;
import fc.ul.scrimfinder.exception.QueueNotFoundException;
import fc.ul.scrimfinder.util.Region;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PlayerRankingService {
    List<PlayerRankingDTO> getPlayerRanking(Long playerId, Optional<Long> queueId)
            throws PlayerNotFoundException, QueueNotFoundException;

    Map<Long, PlayerRankingDTO> processMatchResults(@Valid MatchResultRequest matchResultRequest)
            throws PlayerNotFoundException, QueueNotFoundException;

    PaginatedResponseDTO<PlayerRankingDTO> getQueueLeaderboard(
            int page, int size, Optional<Long> queueId, Optional<Region> region);

    PlayerRankingDTO populatePlayerMMR(Long playerId, @Valid CreatePlayerRequest createPlayerRequest)
            throws MMRAlreadyExistsException, QueueNotFoundException, LeagueAccountNotLinkedException;
}
