package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.request.JoinQueueRequest;
import fc.ul.scrimfinder.dto.response.LobbyDTO;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.dto.response.MatchTicketDTO;

public interface MatchmakingService {
    MatchTicketDTO joinQueue(JoinQueueRequest request);

    void leaveQueue(Long ticketId);

    LobbyDTO getLobbyByTicket(Long ticketId);

    MatchDTO acceptMatch(Long matchId, Long playerId);

    void declineMatch(Long matchId, Long playerId);

    void linkMatch(Long matchId, String externalGameId);

    void completeMatch(Long matchId);
}
