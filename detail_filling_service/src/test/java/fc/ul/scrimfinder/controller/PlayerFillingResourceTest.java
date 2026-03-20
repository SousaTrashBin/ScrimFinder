package fc.ul.scrimfinder.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fc.ul.scrimfinder.client.RiotAccountServiceClient;
import fc.ul.scrimfinder.client.RiotPlayerServiceClient;
import fc.ul.scrimfinder.client.RiotRegionServiceClient;
import fc.ul.scrimfinder.client.RiotSummonerServiceClient;
import fc.ul.scrimfinder.dto.response.player.*;
import fc.ul.scrimfinder.util.Rank;
import fc.ul.scrimfinder.util.Tier;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.json.Json;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PlayerFillingResourceTest {

    @InjectMock
    @RestClient
    RiotAccountServiceClient riotAccountServiceClient;

    @InjectMock
    @RestClient
    RiotRegionServiceClient riotRegionServiceClient;

    @InjectMock
    @RestClient
    RiotSummonerServiceClient riotSummonerServiceClient;

    @InjectMock
    @RestClient
    RiotPlayerServiceClient riotPlayerServiceClient;

    @Test
    @Order(1)
    public void testGetPlayerFillEndpoint() throws JsonProcessingException {
        final String path = "/players";

        final String puuid = UUID.randomUUID().toString();

        final String accountRiotDTO =
                Json.createObjectBuilder()
                        .add("puuid", puuid)
                        .add("gameName", "kung")
                        .add("tagLine", "foo")
                        .build()
                        .toString();

        final String regionRiotDTO =
                Json.createObjectBuilder().add("region", "euw1").build().toString();

        final String summonerRiotDTO =
                Json.createObjectBuilder()
                        .add("profileIconId", 1)
                        .add("summonerLevel", 10L)
                        .build()
                        .toString();

        final String playerQueueStatsRiotDTO =
                Set.of(
                                Json.createObjectBuilder()
                                        .add("queueType", "RANKED_FOO")
                                        .add("tier", "emerald")
                                        .add("rank", "I")
                                        .add("leaguePoints", 5L)
                                        .add("wins", 0)
                                        .add("losses", 30)
                                        .add("hotStreak", false)
                                        .build()
                                        .toString())
                        .toString();

        final AccountDTO accountDTO = new AccountDTO(puuid, "kung", "foo");

        final RegionDTO regionDTO = new RegionDTO("europe", "euw1");

        final SummonerDTO summonerDTO = new SummonerDTO(1, 10L);

        final PlayerQueueStatsDTO queueDTO =
                new PlayerQueueStatsDTO("RANKED_FOO", new Rank(Tier.EMERALD, 1, 5), 0, 30, false);

        final PlayerDTO playerDTO = new PlayerDTO(accountDTO, regionDTO, summonerDTO, Set.of(queueDTO));

        when(riotAccountServiceClient.getByRiotId(anyString(), anyString())).thenReturn(accountRiotDTO);
        when(riotRegionServiceClient.getActiveRegion(anyString())).thenReturn(regionRiotDTO);
        when(riotSummonerServiceClient.getByAccessToken(anyString())).thenReturn(summonerRiotDTO);
        when(riotPlayerServiceClient.getLeagueEntriesByPUUID(anyString()))
                .thenReturn(playerQueueStatsRiotDTO);

        given()
                .when()
                .get(String.format("%s/%s/%s", path, accountDTO.name(), accountDTO.tag()))
                .then()
                .statusCode(200)
                .body(is(new ObjectMapper().writeValueAsString(playerDTO)));
    }
}
