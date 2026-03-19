package fc.ul.scrimfinder.controller;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import fc.ul.scrimfinder.dto.request.JoinQueueRequest;
import fc.ul.scrimfinder.dto.response.MatchTicketDTO;
import fc.ul.scrimfinder.service.MatchmakingService;
import fc.ul.scrimfinder.util.TicketStatus;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class MatchmakingResourceTest {

    @InjectMock MatchmakingService matchmakingService;

    @Test
    void testJoinQueueEndpoint() {
        MatchTicketDTO expectedTicket = new MatchTicketDTO();
        expectedTicket.setId(1L);
        expectedTicket.setPlayerId(100L);
        expectedTicket.setQueueId(1L);
        expectedTicket.setStatus(TicketStatus.IN_QUEUE);
        expectedTicket.setMmr(1200);
        expectedTicket.setCreatedAt(LocalDateTime.now());

        when(matchmakingService.joinQueue(any(JoinQueueRequest.class))).thenReturn(expectedTicket);

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new JoinQueueRequest(100L, 1L, null))
                .when()
                .post("/tickets")
                .then()
                .statusCode(202)
                .body("id", is(1))
                .body("playerId", is(100))
                .body("status", is("IN_QUEUE"));
    }

    @Test
    void testLeaveQueueEndpoint() {
        given().when().delete("/tickets/1").then().statusCode(204);
    }

    @Test
    void testAcceptMatchEndpoint() {
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .queryParam("playerId", 100L)
                .when()
                .post("/tickets/matches/1/accept")
                .then()
                .statusCode(200);
    }
}
