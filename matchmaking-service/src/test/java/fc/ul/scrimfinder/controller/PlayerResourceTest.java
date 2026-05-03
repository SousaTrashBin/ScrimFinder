package fc.ul.scrimfinder.controller;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import fc.ul.scrimfinder.dto.response.LobbyDTO;
import fc.ul.scrimfinder.dto.response.MatchTicketDTO;
import fc.ul.scrimfinder.dto.response.PlayerDTO;
import fc.ul.scrimfinder.service.MatchmakingService;
import fc.ul.scrimfinder.service.PlayerService;
import fc.ul.scrimfinder.util.Region;
import fc.ul.scrimfinder.util.TicketStatus;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class PlayerResourceTest {

    @InjectMock PlayerService playerService;
    @InjectMock MatchmakingService matchmakingService;

    @Test
    void testGetPlayerEndpoint() {
        UUID playerId = UUID.randomUUID();
        PlayerDTO dto = new PlayerDTO();
        dto.setId(playerId);
        dto.setDiscordUsername("test-user");

        when(playerService.getPlayer(eq(playerId))).thenReturn(dto);

        given()
                .when()
                .get("/players/" + playerId)
                .then()
                .statusCode(200)
                .body("id", is(playerId.toString()));
    }

    @Test
    void testGetPlayerTicketsEndpoint() {
        UUID playerId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();

        MatchTicketDTO ticket = new MatchTicketDTO();
        ticket.setId(ticketId);
        ticket.setPlayerId(playerId);
        ticket.setQueueId(queueId);
        ticket.setRegion(Region.EUW);
        ticket.setStatus(TicketStatus.IN_QUEUE);
        ticket.setMmr(1234);

        when(matchmakingService.getTicketsByPlayer(eq(playerId))).thenReturn(List.of(ticket));

        given()
                .when()
                .get("/players/" + playerId + "/tickets")
                .then()
                .statusCode(200)
                .body("[0].id", is(ticketId.toString()))
                .body("[0].playerId", is(playerId.toString()))
                .body("[0].mmr", is(1234));
    }

    @Test
    void testGetPlayerLobbiesEndpoint() {
        UUID playerId = UUID.randomUUID();
        UUID lobbyId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();

        LobbyDTO lobby = new LobbyDTO();
        lobby.setId(lobbyId);
        lobby.setQueueId(queueId);
        lobby.setMatchId(matchId);
        lobby.setRegion(Region.EUW);

        when(matchmakingService.getLobbiesByPlayer(eq(playerId))).thenReturn(List.of(lobby));

        given()
                .when()
                .get("/players/" + playerId + "/lobbies")
                .then()
                .statusCode(200)
                .body("[0].id", is(lobbyId.toString()))
                .body("[0].queueId", is(queueId.toString()))
                .body("[0].matchId", is(matchId.toString()));
    }
}
