package fc.ul.scrimfinder.controller;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import fc.ul.scrimfinder.dto.response.PlayerDTO;
import fc.ul.scrimfinder.dto.response.RiotAccountDTO;
import fc.ul.scrimfinder.service.PlayerService;
import fc.ul.scrimfinder.util.Region;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class PlayerResourceTest {

    @InjectMock PlayerService playerService;

    @Test
    void testGetPlayerEndpoint() {
        UUID playerId = UUID.randomUUID();
        PlayerDTO dto = new PlayerDTO(playerId, "discord_user", List.of(), 1100, 1200);

        when(playerService.getPlayer(eq(playerId))).thenReturn(dto);

        given()
                .when()
                .get("/players/" + playerId)
                .then()
                .statusCode(200)
                .body("id", is(playerId.toString()))
                .body("discordUsername", is("discord_user"));
    }

    @Test
    void testGetPrimaryAccountEndpoint() {
        UUID playerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        RiotAccountDTO account =
                new RiotAccountDTO(accountId, "puuid-1", "GameName", "EUW", Region.EUW, true);

        when(playerService.getPrimaryAccount(eq(playerId))).thenReturn(account);

        given()
                .when()
                .get("/players/" + playerId + "/primary-account")
                .then()
                .statusCode(200)
                .body("id", is(accountId.toString()))
                .body("puuid", is("puuid-1"))
                .body("isPrimary", is(true));
    }
}
