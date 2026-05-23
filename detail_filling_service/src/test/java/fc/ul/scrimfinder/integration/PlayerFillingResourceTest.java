package fc.ul.scrimfinder.integration;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.*;

@QuarkusTest
@Tag("integration-light")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PlayerFillingResourceTest {

    private static final String PLAYER_PATH = "/players";
    private static final String SERVER = "EUW";
    private static final String PLAYER_NAME = "sousa";
    private static final String PLAYER_TAG = "balls";
    private static final String NON_EXISTENT_PLAYER_TAG = "ball";

    @Test
    @Order(1)
    public void testGetPlayerFillEndpoint_ShouldReturnOK_WhenPlayerExists() {
        given()
                .when()
                .get(String.format("%s/%s/%s/%s", PLAYER_PATH, SERVER, PLAYER_NAME, PLAYER_TAG))
                .then()
                .statusCode(200);
    }

    @Test
    @Order(2)
    public void testGetPlayerFillEndpoint_ShouldReturnNotFound_WhenPlayerNotExists() {
        given()
                .when()
                .get(
                        String.format("%s/%s/%s/%s", PLAYER_PATH, SERVER, PLAYER_NAME, NON_EXISTENT_PLAYER_TAG))
                .then()
                .statusCode(404);
    }
}
