package fc.ul.scrimfinder.controller;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.dto.response.PlayerRankingDTO;
import fc.ul.scrimfinder.service.PlayerRankingService;
import fc.ul.scrimfinder.util.Region;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class LeaderboardResourceTest {

    @InjectMock PlayerRankingService playerRankingService;

    @Test
    void testGetLeaderboardEndpoint() {
        PlayerRankingDTO ranking =
                new PlayerRankingDTO(
                        UUID.randomUUID(), 100L, "User100", "P100", Region.EUW, 1L, 1200, 10, 5);

        PaginatedResponseDTO<PlayerRankingDTO> response =
                new PaginatedResponseDTO<>(List.of(ranking), 0, 1, 1L);

        when(playerRankingService.getQueueLeaderboard(anyInt(), anyInt(), any(), any()))
                .thenReturn(response);

        given()
                .when()
                .get("/leaderboards?queueId=1&region=EUW")
                .then()
                .statusCode(200)
                .body("currentPage", is(0))
                .body("totalElements", is(1))
                .body("data[0].playerId", is(100));
    }
}
