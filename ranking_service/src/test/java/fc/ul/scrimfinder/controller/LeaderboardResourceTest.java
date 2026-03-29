package fc.ul.scrimfinder.controller;

import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.dto.response.PlayerRankingDTO;
import fc.ul.scrimfinder.service.PlayerRankingService;
import fc.ul.scrimfinder.util.Region;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@QuarkusTest
public class LeaderboardResourceTest {

    @InjectMock PlayerRankingService playerRankingService;

    @Test
    void testGetLeaderboardEndpoint() {
        UUID rid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        UUID qid = UUID.randomUUID();
        PlayerRankingDTO ranking =
                new PlayerRankingDTO(rid, pid, "User100", "P100", Region.EUW, qid, 1200, 10, 5);

        PaginatedResponseDTO<PlayerRankingDTO> response =
                new PaginatedResponseDTO<>(List.of(ranking), 0, 1, 1L);

        when(playerRankingService.getQueueLeaderboard(anyInt(), anyInt(), any(), any()))
                .thenReturn(response);

        given()
                .when()
                .get("/leaderboards?queueId=" + qid + "&region=EUW")
                .then()
                .statusCode(200)
                .body("currentPage", is(0))
                .body("totalElements", is(1))
                .body("data[0].playerId", is(pid.toString()));
    }
}
