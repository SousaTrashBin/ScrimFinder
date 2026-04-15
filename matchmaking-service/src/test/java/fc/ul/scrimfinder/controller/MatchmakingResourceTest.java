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
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class MatchmakingResourceTest {

    @InjectMock MatchmakingService matchmakingService;

    @Test
    void testJoinQueueEndpoint() {
        UUID tid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        UUID qid = UUID.randomUUID();
        MatchTicketDTO expectedTicket = new MatchTicketDTO();
        expectedTicket.setId(tid);
        expectedTicket.setPlayerId(pid);
        expectedTicket.setQueueId(qid);
        expectedTicket.setStatus(TicketStatus.IN_QUEUE);
        expectedTicket.setMmr(1200);
        expectedTicket.setCreatedAt(LocalDateTime.now());

        when(matchmakingService.joinQueue(any(JoinQueueRequest.class))).thenReturn(expectedTicket);

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new JoinQueueRequest(pid, qid, null))
                .when()
                .post("/tickets")
                .then()
                .statusCode(202)
                .body("id", is(tid.toString()))
                .body("playerId", is(pid.toString()))
                .body("status", is("IN_QUEUE"));
    }

    @Test
    void testLeaveQueueEndpoint() {
        UUID tid = UUID.randomUUID();
        given().when().delete("/tickets/" + tid).then().statusCode(204);
    }

    @Test
    void testAcceptMatchEndpoint() {
        UUID mid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .queryParam("playerId", pid.toString())
                .when()
                .post("/tickets/matches/" + mid + "/accept")
                .then()
                .statusCode(200);
    }
}
