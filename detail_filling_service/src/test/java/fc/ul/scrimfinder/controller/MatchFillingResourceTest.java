package fc.ul.scrimfinder.controller;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fc.ul.scrimfinder.client.RiotMatchServiceClient;
import fc.ul.scrimfinder.dto.response.match.MatchStatsDTO;
import fc.ul.scrimfinder.dto.response.match.PlayerStatsDTO;
import fc.ul.scrimfinder.dto.response.match.TeamStatsDTO;
import fc.ul.scrimfinder.redis.RedisService;
import fc.ul.scrimfinder.util.RiotId;
import fc.ul.scrimfinder.util.Role;
import fc.ul.scrimfinder.util.TeamSide;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.json.Json;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MatchFillingResourceTest {

    @InjectMock @RestClient RiotMatchServiceClient riotMatchServiceClient;

    @InjectMock RedisService redisService;

    @Test
    @Order(1)
    public void testGetMatchRawEndpoint() {
        final String path = "/matches";

        final String matchId = "EUW1_" + UUID.randomUUID();

        when(riotMatchServiceClient.getMatch(anyString())).thenReturn("");
        when(redisService.get(anyString(), any())).thenReturn(Optional.empty());

        given().when().get(String.format("%s/%s/raw", path, matchId)).then().statusCode(200);
    }

    @Test
    @Order(2)
    public void testGetMatchFillEndpoint() throws JsonProcessingException {
        final String path = "/matches";

        final String matchId = "EUW1_" + UUID.randomUUID();

        final String matchRiotDTO =
                Json.createObjectBuilder()
                        .add("metadata", Json.createObjectBuilder().add("matchId", matchId).build())
                        .add(
                                "info",
                                Json.createObjectBuilder()
                                        .add("queueId", 1L)
                                        .add("gameVersion", "15.5.8421098.0")
                                        .add("gameStartTimestamp", 100000L)
                                        .add("gameDuration", 1000L)
                                        .add(
                                                "participants",
                                                Json.createArrayBuilder()
                                                        .add(
                                                                Json.createObjectBuilder()
                                                                        .add("riotIdGameName", "kung")
                                                                        .add("riotIdTagline", "foo")
                                                                        .add("profileIcon", 1L)
                                                                        .add("kills", 27)
                                                                        .add("deaths", 30)
                                                                        .add("assists", 5)
                                                                        .add("totalHeal", 1000)
                                                                        .add("totalDamageDealt", 4000)
                                                                        .add("wardsPlaced", 7)
                                                                        .add("goldEarned", 1234)
                                                                        .add("teamPosition", "TOP")
                                                                        .add("championName", "Morgana")
                                                                        .add("totalMinionsKilled", 4)
                                                                        .add("tripleKills", 3)
                                                                        .add("quadraKills", 2)
                                                                        .add("pentaKills", 1)
                                                                        .add("teamId", 100)
                                                                        .add("win", true)
                                                                        .build())
                                                        .add(
                                                                Json.createObjectBuilder()
                                                                        .add("riotIdGameName", "TaiLung")
                                                                        .add("riotIdTagline", "nofoo")
                                                                        .add("profileIcon", 2L)
                                                                        .add("kills", 5)
                                                                        .add("deaths", 30)
                                                                        .add("assists", 5)
                                                                        .add("totalHeal", 0)
                                                                        .add("totalDamageDealt", 6000)
                                                                        .add("wardsPlaced", 2)
                                                                        .add("goldEarned", 1000)
                                                                        .add("teamPosition", "JUNGLE")
                                                                        .add("championName", "Alistar")
                                                                        .add("totalMinionsKilled", 6)
                                                                        .add("tripleKills", 2)
                                                                        .add("quadraKills", 1)
                                                                        .add("pentaKills", 0)
                                                                        .add("teamId", 200)
                                                                        .add("win", false)
                                                                        .build())
                                                        .build())
                                        .add(
                                                "teams",
                                                Json.createArrayBuilder()
                                                        .add(Json.createObjectBuilder().add("teamId", 100).build())
                                                        .add(Json.createObjectBuilder().add("teamId", 200).build()))
                                        .build())
                        .build()
                        .toString();

        final List<PlayerStatsDTO> playerStatsDTOList =
                List.of(
                        new PlayerStatsDTO(
                                new RiotId("kung", "foo", 1),
                                27,
                                30,
                                5,
                                1000,
                                4000,
                                7,
                                1234,
                                Role.TOP,
                                "Morgana",
                                4,
                                3,
                                2,
                                1,
                                TeamSide.BLUE,
                                true),
                        new PlayerStatsDTO(
                                new RiotId("TaiLung", "nofoo", 2),
                                5,
                                30,
                                5,
                                0,
                                6000,
                                2,
                                1000,
                                Role.JUNGLE,
                                "Alistar",
                                6,
                                2,
                                1,
                                0,
                                TeamSide.RED,
                                false));

        final List<TeamStatsDTO> teamStatsDTOList =
                List.of(
                        new TeamStatsDTO(TeamSide.BLUE, 27, 30, 5, 1000),
                        new TeamStatsDTO(TeamSide.RED, 5, 30, 5, 0));

        final MatchStatsDTO matchStatsDTO =
                new MatchStatsDTO(
                        matchId, 1L, "15.5", 100000L, 1000L, playerStatsDTOList, teamStatsDTOList);

        when(riotMatchServiceClient.getMatch(anyString())).thenReturn(matchRiotDTO);
        when(redisService.get(anyString(), any())).thenReturn(Optional.empty());

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        given()
                .when()
                .get(String.format("%s/%s", path, matchId))
                .then()
                .statusCode(200)
                .body(is(mapper.writeValueAsString(matchStatsDTO)));
    }
}
