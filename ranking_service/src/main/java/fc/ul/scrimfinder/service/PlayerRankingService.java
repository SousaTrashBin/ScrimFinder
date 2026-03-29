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
import java.util.UUID;

public interface PlayerRankingService {
    List<PlayerRankingDTO> getPlayerRanking(UUID playerId, Optional<UUID> queueId)
            throws PlayerNotFoundException, QueueNotFoundException;

    Map<UUID, PlayerRankingDTO> processMatchResults(@Valid MatchResultRequest matchResultRequest)
            throws PlayerNotFoundException, QueueNotFoundException;

    PaginatedResponseDTO<PlayerRankingDTO> getQueueLeaderboard(
            int page, int size, Optional<UUID> queueId, Optional<Region> region);

    PlayerRankingDTO populatePlayerMMR(UUID playerId, @Valid CreatePlayerRequest createPlayerRequest)
            throws MMRAlreadyExistsException, QueueNotFoundException, LeagueAccountNotLinkedException;
}
