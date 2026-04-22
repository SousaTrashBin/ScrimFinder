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
        UUID rid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        UUID qid = UUID.randomUUID();
        PlayerRankingDTO ranking =
                new PlayerRankingDTO(rid, pid, "User100", "P100", Region.EUW, qid, 1200, 10, 5);

        when(playerRankingService.getPlayerRanking(eq(pid), any())).thenReturn(List.of(ranking));

        given()
                .when()
                .get("/players/" + pid + "/queue?queueId=" + qid)
                .then()
                .statusCode(200)
                .body("[0].playerId", is(pid.toString()))
                .body("[0].mmr", is(1200));
    }
}
