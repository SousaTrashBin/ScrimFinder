package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.request.JoinQueueRequest;
import fc.ul.scrimfinder.dto.response.LobbyDTO;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.dto.response.MatchTicketDTO;
import java.util.List;
import java.util.UUID;

public interface MatchmakingService {
    MatchTicketDTO joinQueue(JoinQueueRequest request);

    void leaveQueue(UUID ticketId);

    LobbyDTO getLobbyByTicket(UUID ticketId);

    List<MatchTicketDTO> getTicketsByPlayer(UUID playerId);

    List<LobbyDTO> getLobbiesByPlayer(UUID playerId);

    MatchDTO acceptMatch(UUID matchId, UUID playerId);

    void declineMatch(UUID matchId, UUID playerId);

    void linkMatch(UUID matchId, String externalGameId);

    void completeMatch(UUID matchId);
}
