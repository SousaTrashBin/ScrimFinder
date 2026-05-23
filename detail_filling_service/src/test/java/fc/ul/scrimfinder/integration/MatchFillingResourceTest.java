package fc.ul.scrimfinder.integration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fc.ul.scrimfinder.dto.response.match.MatchStatsDTO;
import fc.ul.scrimfinder.dto.response.match.PlayerStatsDTO;
import fc.ul.scrimfinder.dto.response.match.TeamStatsDTO;
import fc.ul.scrimfinder.util.RiotId;
import fc.ul.scrimfinder.util.Role;
import fc.ul.scrimfinder.util.TeamSide;
import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import org.junit.jupiter.api.*;

@QuarkusTest
@Tag("integration-light")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MatchFillingResourceTest {

    private static final String MATCH_PATH = "/matches";
    private static final String MATCH_ID = "EUW1_7779779801";
    private static final String NON_EXISTENT_MATCH_ID = "EUW1_7779779800";

    @Test
    @Order(1)
    public void testGetMatchRawEndpoint_ShouldReturnOK_WhenMatchExists() {
        given().when().get(String.format("%s/%s/raw", MATCH_PATH, MATCH_ID)).then().statusCode(200);
    }

    @Test
    @Order(2)
    public void testGetMatchFillEndpoint_ShouldReturnFilteredMatch_WhenMatchExists()
            throws JsonProcessingException {
        final MatchStatsDTO expectedMatchDTO =
                new MatchStatsDTO(
                        MATCH_ID,
                        "16.5",
                        1773524078235L,
                        1362L,
                        List.of(
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "h_gAfdHVoI8vBJaIyyGOUqDJlXUGGePLUJdMpR1zoRVPdigrRKvMchFBAzIlOUpd4BLlr6Ba8BRSjw",
                                                "Maynter",
                                                "EUW",
                                                1301,
                                                787),
                                        1,
                                        5,
                                        1,
                                        3776,
                                        64721,
                                        7,
                                        6451,
                                        Role.TOP,
                                        "Gnar",
                                        139,
                                        0,
                                        0,
                                        0,
                                        TeamSide.BLUE,
                                        false),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "OR5LGqOXhHVe8xj0IWrZHJWvOqLMVprQYTKbhawJQjzIFjDaFwCmnLc8Eid0rWTsZ6DRVCz65lsjrw",
                                                "Fentasm",
                                                "FENT",
                                                6278,
                                                656),
                                        1,
                                        7,
                                        4,
                                        5874,
                                        142137,
                                        4,
                                        8100,
                                        Role.JUNGLE,
                                        "Vayne",
                                        33,
                                        0,
                                        0,
                                        0,
                                        TeamSide.BLUE,
                                        false),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "aqzT2pS2aQuBrkKv4izgg99_X73edM43rJoSbOhEhWg1lThgMB4yDcQsU8hGtXVTZnQmAYOawLMxeA",
                                                "SlayderW",
                                                "EUWW",
                                                28,
                                                103),
                                        4,
                                        5,
                                        2,
                                        759,
                                        122557,
                                        5,
                                        9823,
                                        Role.MID,
                                        "Orianna",
                                        211,
                                        0,
                                        0,
                                        0,
                                        TeamSide.BLUE,
                                        false),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "neqSH5XmjVkKkOki4MDq5Re0WwHMJ8BKsh_URZjThuWOINOoYvoe-YNU8Ll48ojGN5oCn4owcd2UQg",
                                                "KISS OF DEATH",
                                                "YEN",
                                                1455,
                                                476),
                                        6,
                                        9,
                                        4,
                                        1315,
                                        69636,
                                        7,
                                        9597,
                                        Role.BOTTOM,
                                        "Ashe",
                                        146,
                                        0,
                                        0,
                                        0,
                                        TeamSide.BLUE,
                                        false),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "5GC_pKq053P0rngh7uTcjwB79V4vs1JoA3wzcUYgqlRr6tB-iNEF51CJnOQKrk4daGa5g8AdNezprw",
                                                "Last dance",
                                                "Hexom",
                                                1637,
                                                106),
                                        1,
                                        9,
                                        8,
                                        4690,
                                        23399,
                                        24,
                                        6350,
                                        Role.SUPPORT,
                                        "Seraphine",
                                        29,
                                        0,
                                        0,
                                        0,
                                        TeamSide.BLUE,
                                        false),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "Yhpm3VDVVQ09nAF9qUANOLFyItHikNj54vxB1yMzHmqIEohkIKEAeX-al94bBnYWtmsYv5CN6W3BvQ",
                                                "baby kiin",
                                                "keem",
                                                6,
                                                120),
                                        5,
                                        3,
                                        5,
                                        2833,
                                        144290,
                                        10,
                                        11484,
                                        Role.TOP,
                                        "Jayce",
                                        191,
                                        0,
                                        0,
                                        0,
                                        TeamSide.RED,
                                        true),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "avrrbs2xmpx-CjjNFWyRENlXRmLPTN4X-igl6r-I732hIPmTV_e5NlJ3W4qVJ58inDq4hY_6e5sqOA",
                                                "J1HUIV",
                                                "000",
                                                29,
                                                445),
                                        11,
                                        1,
                                        6,
                                        9874,
                                        295508,
                                        4,
                                        13424,
                                        Role.JUNGLE,
                                        "Qiyana",
                                        39,
                                        1,
                                        0,
                                        0,
                                        TeamSide.RED,
                                        true),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "CFcq57-yO1Shwn5cVI0nkIHWIGgzl-QV1g5qMAliHHZuFo_HBoX-S6ZjhidAKLenTr5ziJAHXxw-aQ",
                                                "Yoriichı",
                                                "EUW",
                                                5045,
                                                210),
                                        5,
                                        1,
                                        5,
                                        6144,
                                        140039,
                                        5,
                                        10871,
                                        Role.MID,
                                        "Ahri",
                                        221,
                                        0,
                                        0,
                                        0,
                                        TeamSide.RED,
                                        true),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "vyzwo-B74dAgHNsstNoWPioVU8bLh6kye-0xtdFTMQwM4Y-kCSZfwMpf29CadJJ1AvpYEPV5yNMicA",
                                                "KC NEXT ADKING",
                                                "EUW",
                                                29,
                                                727),
                                        13,
                                        3,
                                        12,
                                        12959,
                                        110972,
                                        10,
                                        13924,
                                        Role.BOTTOM,
                                        "Senna",
                                        165,
                                        0,
                                        0,
                                        0,
                                        TeamSide.RED,
                                        true),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "fwP-3K-Z_ghLAruQYlntd03YSdRCJeD7kaJQFujGJJpiwZ-ie9Rwkc9SmsiExqcnbnssXGmoAtyqHA",
                                                "berebooo",
                                                "TR1",
                                                3158,
                                                346),
                                        1,
                                        6,
                                        26,
                                        3544,
                                        15953,
                                        33,
                                        8253,
                                        Role.SUPPORT,
                                        "Blitzcrank",
                                        15,
                                        0,
                                        0,
                                        0,
                                        TeamSide.RED,
                                        true)),
                        List.of(
                                new TeamStatsDTO(TeamSide.BLUE, 13, 35, 19, 16414),
                                new TeamStatsDTO(TeamSide.RED, 35, 14, 54, 35354)));

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        given()
                .when()
                .get(String.format("%s/%s", MATCH_PATH, MATCH_ID))
                .then()
                .statusCode(200)
                .body(is(mapper.writeValueAsString(expectedMatchDTO)));
    }

    @Test
    @Order(3)
    public void testGetMatchFillEndpoint_ShouldReturnNotFound_WhenMatchNotExists() {
        given()
                .when()
                .get(String.format("%s/%s", MATCH_PATH, NON_EXISTENT_MATCH_ID))
                .then()
                .statusCode(404);
    }
}
