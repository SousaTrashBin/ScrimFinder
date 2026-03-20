package fc.ul.scrimfinder.controller;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import fc.ul.scrimfinder.dto.response.PlayerRankingDTO;
import fc.ul.scrimfinder.service.PlayerRankingService;
import fc.ul.scrimfinder.util.Region;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class PlayerRankingResourceTest {

    @InjectMock PlayerRankingService playerRankingService;

    @Test
    void testGetPlayerRankingEndpoint() {
        PlayerRankingDTO ranking =
                new PlayerRankingDTO(
                        UUID.randomUUID(), 100L, "User100", "P100", Region.EUW, 1L, 1200, 10, 5);

        when(playerRankingService.getPlayerRanking(eq(100L), any())).thenReturn(List.of(ranking));

        given()
                .when()
                .get("/players/100/queue?queueId=1")
                .then()
                .statusCode(200)
                .body("playerId", is(100))
                .body("mmr", is(1200));
    }
}
