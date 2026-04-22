package fc.ul.scrimfinder.controller;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fc.ul.scrimfinder.dto.request.PlayerStatsDTO;
import fc.ul.scrimfinder.dto.request.TeamStatsDTO;
import fc.ul.scrimfinder.dto.request.filtering.MatchFilters;
import fc.ul.scrimfinder.dto.request.filtering.PlayerFilters;
import fc.ul.scrimfinder.dto.request.filtering.TeamFilters;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.service.DetailFillingAdapterService;
import fc.ul.scrimfinder.service.TrainingAdapterService;
import fc.ul.scrimfinder.util.Champion;
import fc.ul.scrimfinder.util.RiotId;
import fc.ul.scrimfinder.util.Role;
import fc.ul.scrimfinder.util.TeamSide;
import fc.ul.scrimfinder.util.interval.IntegerInterval;
import fc.ul.scrimfinder.util.interval.LongInterval;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MatchHistoryResourceTest {

    @InjectMock TrainingAdapterService trainingAdapterService;

    @InjectMock DetailFillingAdapterService detailFillingAdapterService;

    final ObjectMapper MAPPER = new ObjectMapper();

    final String riotMatchId = "EUW1_7779779801";

    final MatchDTO expectedMatchDTO =
            new MatchDTO(
                    riotMatchId,
                    UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
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
                                    Champion.GNAR,
                                    6.1,
                                    139,
                                    0,
                                    0,
                                    0,
                                    TeamSide.BLUE,
                                    false,
                                    1),
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
                                    Champion.VAYNE,
                                    1.5,
                                    33,
                                    0,
                                    0,
                                    0,
                                    TeamSide.BLUE,
                                    false,
                                    2),
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
                                    Champion.ORIANNA,
                                    9.3,
                                    211,
                                    0,
                                    0,
                                    0,
                                    TeamSide.BLUE,
                                    false,
                                    3),
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
                                    Champion.ASHE,
                                    6.4,
                                    146,
                                    0,
                                    0,
                                    0,
                                    TeamSide.BLUE,
                                    false,
                                    4),
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
                                    Champion.SERAPHINE,
                                    1.3,
                                    29,
                                    0,
                                    0,
                                    0,
                                    TeamSide.BLUE,
                                    false,
                                    5),
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
                                    Champion.JAYCE,
                                    8.4,
                                    191,
                                    0,
                                    0,
                                    0,
                                    TeamSide.RED,
                                    true,
                                    -1),
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
                                    Champion.QIYANA,
                                    1.7,
                                    39,
                                    1,
                                    0,
                                    0,
                                    TeamSide.RED,
                                    true,
                                    -2),
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
                                    Champion.AHRI,
                                    9.7,
                                    221,
                                    0,
                                    0,
                                    0,
                                    TeamSide.RED,
                                    true,
                                    -3),
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
                                    Champion.SENNA,
                                    7.3,
                                    165,
                                    0,
                                    0,
                                    0,
                                    TeamSide.RED,
                                    true,
                                    -4),
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
                                    Champion.BLITZCRANK,
                                    0.7,
                                    15,
                                    0,
                                    0,
                                    0,
                                    TeamSide.RED,
                                    true,
                                    -5)),
                    List.of(
                            new TeamStatsDTO(TeamSide.BLUE, 13, 35, 19, 16414),
                            new TeamStatsDTO(TeamSide.RED, 35, 14, 54, 35354)));

    @Test
    @Order(1)
    public void testAddMatchEndpoint() throws JsonProcessingException {
        final String path = "/matches";
        final String queueId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        final Map<String, Integer> mmrDeltas =
                Map.of(
                        "h_gAfdHVoI8vBJaIyyGOUqDJlXUGGePLUJdMpR1zoRVPdigrRKvMchFBAzIlOUpd4BLlr6Ba8BRSjw", 1,
                        "OR5LGqOXhHVe8xj0IWrZHJWvOqLMVprQYTKbhawJQjzIFjDaFwCmnLc8Eid0rWTsZ6DRVCz65lsjrw", 2,
                        "aqzT2pS2aQuBrkKv4izgg99_X73edM43rJoSbOhEhWg1lThgMB4yDcQsU8hGtXVTZnQmAYOawLMxeA", 3,
                        "neqSH5XmjVkKkOki4MDq5Re0WwHMJ8BKsh_URZjThuWOINOoYvoe-YNU8Ll48ojGN5oCn4owcd2UQg", 4,
                        "5GC_pKq053P0rngh7uTcjwB79V4vs1JoA3wzcUYgqlRr6tB-iNEF51CJnOQKrk4daGa5g8AdNezprw", 5,
                        "Yhpm3VDVVQ09nAF9qUANOLFyItHikNj54vxB1yMzHmqIEohkIKEAeX-al94bBnYWtmsYv5CN6W3BvQ", -1,
                        "avrrbs2xmpx-CjjNFWyRENlXRmLPTN4X-igl6r-I732hIPmTV_e5NlJ3W4qVJ58inDq4hY_6e5sqOA", -2,
                        "CFcq57-yO1Shwn5cVI0nkIHWIGgzl-QV1g5qMAliHHZuFo_HBoX-S6ZjhidAKLenTr5ziJAHXxw-aQ", -3,
                        "vyzwo-B74dAgHNsstNoWPioVU8bLh6kye-0xtdFTMQwM4Y-kCSZfwMpf29CadJJ1AvpYEPV5yNMicA", -4,
                        "fwP-3K-Z_ghLAruQYlntd03YSdRCJeD7kaJQFujGJJpiwZ-ie9Rwkc9SmsiExqcnbnssXGmoAtyqHA", -5);

        when(trainingAdapterService.sendMatchForAnalysis(anyString())).thenReturn(true);
        when(detailFillingAdapterService.getMatch(any())).thenReturn(expectedMatchDTO);

        given()
                .contentType(ContentType.JSON)
                .pathParam("riotMatchId", riotMatchId)
                .queryParam("queueId", queueId)
                .body(mmrDeltas)
                .when()
                .post(String.format("%s/{riotMatchId}", path))
                .then()
                .statusCode(201)
                .body(is(MAPPER.writeValueAsString(expectedMatchDTO)));
    }

    @Test
    @Order(2)
    public void testAddMatchAlreadyExists() throws JsonProcessingException {
        final String path = "/matches";
        final String queueId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        final Map<String, Integer> mmrDeltas =
                Map.of(
                        "h_gAfdHVoI8vBJaIyyGOUqDJlXUGGePLUJdMpR1zoRVPdigrRKvMchFBAzIlOUpd4BLlr6Ba8BRSjw", 1,
                        "OR5LGqOXhHVe8xj0IWrZHJWvOqLMVprQYTKbhawJQjzIFjDaFwCmnLc8Eid0rWTsZ6DRVCz65lsjrw", 2,
                        "aqzT2pS2aQuBrkKv4izgg99_X73edM43rJoSbOhEhWg1lThgMB4yDcQsU8hGtXVTZnQmAYOawLMxeA", 3,
                        "neqSH5XmjVkKkOki4MDq5Re0WwHMJ8BKsh_URZjThuWOINOoYvoe-YNU8Ll48ojGN5oCn4owcd2UQg", 4,
                        "5GC_pKq053P0rngh7uTcjwB79V4vs1JoA3wzcUYgqlRr6tB-iNEF51CJnOQKrk4daGa5g8AdNezprw", 5,
                        "Yhpm3VDVVQ09nAF9qUANOLFyItHikNj54vxB1yMzHmqIEohkIKEAeX-al94bBnYWtmsYv5CN6W3BvQ", -1,
                        "avrrbs2xmpx-CjjNFWyRENlXRmLPTN4X-igl6r-I732hIPmTV_e5NlJ3W4qVJ58inDq4hY_6e5sqOA", -2,
                        "CFcq57-yO1Shwn5cVI0nkIHWIGgzl-QV1g5qMAliHHZuFo_HBoX-S6ZjhidAKLenTr5ziJAHXxw-aQ", -3,
                        "vyzwo-B74dAgHNsstNoWPioVU8bLh6kye-0xtdFTMQwM4Y-kCSZfwMpf29CadJJ1AvpYEPV5yNMicA", -4,
                        "fwP-3K-Z_ghLAruQYlntd03YSdRCJeD7kaJQFujGJJpiwZ-ie9Rwkc9SmsiExqcnbnssXGmoAtyqHA", -5);

        when(trainingAdapterService.sendMatchForAnalysis(anyString())).thenReturn(true);
        when(detailFillingAdapterService.getMatch(any())).thenReturn(expectedMatchDTO);

        given()
                .contentType(ContentType.JSON)
                .pathParam("riotMatchId", riotMatchId)
                .queryParam("queueId", queueId)
                .body(mmrDeltas)
                .when()
                .post(String.format("%s/{riotMatchId}", path))
                .then()
                .statusCode(400);
    }

    @Test
    @Order(3)
    public void testGetMatchByIdEndpoint() throws JsonProcessingException {
        final String path = "/matches";

        given()
                .pathParam("riotMatchId", riotMatchId)
                .when()
                .get(String.format("%s/{riotMatchId}", path))
                .then()
                .statusCode(200)
                .body(is(MAPPER.writeValueAsString(expectedMatchDTO)));
    }

    @Test
    @Order(4)
    public void testGetFilteredMatchesEndpoint() throws JsonProcessingException {
        // No filters

        final String path = "/matches";
        final int page = 0;
        final int size = 20;

        MatchFilters filters = new MatchFilters();

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(expectedMatchDTO), page, 1, 1);

        given()
                .queryParam("page", page)
                .queryParam("size", size)
                .contentType(ContentType.JSON)
                .body(filters)
                .when()
                .get(path)
                .then()
                .statusCode(200)
                .body(is(MAPPER.writeValueAsString(expectedResponse)));
    }

    @Test
    @Order(5)
    public void testGetFilteredMatchesByTimeEmpty() throws JsonProcessingException {
        // Filter matches by time between 1773524078225 and 1773524078230 (above match started in
        // 1773524078235)

        final String path = "/matches";
        final int page = 0;
        final int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setTime(new LongInterval(1773524078225L, 1773524078230L));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(), page, 0, 0);

        given()
                .queryParam("page", page)
                .queryParam("size", size)
                .contentType(ContentType.JSON)
                .body(filters)
                .when()
                .get(path)
                .then()
                .statusCode(200)
                .body(is(MAPPER.writeValueAsString(expectedResponse)));
    }

    @Test
    @Order(6)
    public void testGetFilteredMatchesByPlayers() throws JsonProcessingException {
        // Filter by matches with player Last dance having role SUPPORT on the blue team with less than
        // 5000 healing
        // and player berebooo winning with a single kill and more than 8000 gold

        final String path = "/matches";
        final int page = 0;
        final int size = 20;

        MatchFilters filters = new MatchFilters();

        PlayerFilters playerFilters1 = new PlayerFilters();
        playerFilters1.setPlayerName("Last dance");
        playerFilters1.setRole(Role.SUPPORT);
        playerFilters1.setTeamSide(TeamSide.BLUE);
        playerFilters1.setHealing(new IntegerInterval(null, 5000));

        PlayerFilters playerFilters2 = new PlayerFilters();
        playerFilters2.setPlayerName("berebooo");
        playerFilters2.setWon(true);
        playerFilters2.setKills(new IntegerInterval(1, 1));
        playerFilters2.setGold(new IntegerInterval(8000, null));

        filters.setPlayers(List.of(playerFilters1, playerFilters2));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(expectedMatchDTO), page, 1, 1);

        given()
                .queryParam("page", page)
                .queryParam("size", size)
                .contentType(ContentType.JSON)
                .body(filters)
                .when()
                .get(path)
                .then()
                .statusCode(200)
                .body(is(MAPPER.writeValueAsString(expectedResponse)));
    }

    @Test
    @Order(7)
    public void testGetFilteredMatchesByChampionsAndPlayerChampions() throws JsonProcessingException {
        // Filter by matches where someone played as champion ASHE
        // and player Fentasm played as one of the following champions: VAYNE, DIANA or JAX

        final String path = "/matches";
        final int page = 0;
        final int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setChampions(List.of(Champion.ASHE));

        PlayerFilters playerFilters = new PlayerFilters();
        playerFilters.setPlayerName("Fentasm");
        playerFilters.setChampions(List.of(Champion.VAYNE, Champion.DIANA, Champion.JAX));

        filters.setPlayers(List.of(playerFilters));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(expectedMatchDTO), page, 1, 1);

        given()
                .queryParam("page", page)
                .queryParam("size", size)
                .contentType(ContentType.JSON)
                .body(filters)
                .when()
                .get(path)
                .then()
                .statusCode(200)
                .body(is(MAPPER.writeValueAsString(expectedResponse)));
    }

    @Test
    @Order(8)
    public void testGetFilteredMatchesByTeams() throws JsonProcessingException {
        // Filter by matches where some team had at least 20 kills
        // and team blue had at most 15 kills and between 30 and 40 deaths

        final String path = "/matches";
        final int page = 0;
        final int size = 20;

        MatchFilters filters = new MatchFilters();

        TeamFilters teamFilters1 = new TeamFilters();
        teamFilters1.setTeamKills(new IntegerInterval(20, null));

        TeamFilters teamFilters2 = new TeamFilters();
        teamFilters2.setTeamSide(TeamSide.BLUE);
        teamFilters2.setTeamKills(new IntegerInterval(null, 15));
        teamFilters2.setTeamDeaths(new IntegerInterval(30, 40));

        filters.setTeams(List.of(teamFilters1, teamFilters2));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(expectedMatchDTO), page, 1, 1);

        given()
                .queryParam("page", page)
                .queryParam("size", size)
                .contentType(ContentType.JSON)
                .body(filters)
                .when()
                .get(path)
                .then()
                .statusCode(200)
                .body(is(MAPPER.writeValueAsString(expectedResponse)));
    }

    @Test
    @Order(9)
    public void testDeleteMatchEndpoint() throws JsonProcessingException {
        final String path = "/matches/{riotMatchId}";

        given()
                .pathParam("riotMatchId", riotMatchId)
                .when()
                .delete(path)
                .then()
                .statusCode(200)
                .body(is(MAPPER.writeValueAsString(expectedMatchDTO)));

        given().pathParam("riotMatchId", riotMatchId).when().get(path).then().statusCode(404);
    }
}
