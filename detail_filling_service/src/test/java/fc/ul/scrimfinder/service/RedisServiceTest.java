package fc.ul.scrimfinder.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.AssertionsKt.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fc.ul.scrimfinder.dto.response.match.MatchStatsDTO;
import fc.ul.scrimfinder.dto.response.match.PlayerStatsDTO;
import fc.ul.scrimfinder.dto.response.match.TeamStatsDTO;
import fc.ul.scrimfinder.dto.response.player.*;
import fc.ul.scrimfinder.redis.RedisService;
import fc.ul.scrimfinder.util.*;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RedisServiceTest {

    @Inject MatchFillingService matchFillingService;

    @Inject PlayerFillingService playerFillingService;

    @Inject RedisService redisService;

    @InjectMock RiotAdapterService riotAdapterService;

    @Test
    @Order(1)
    public void testGetExistentRawMatchFromCache() {
        final String matchId = UUID.randomUUID().toString();
        when(riotAdapterService.getRawMatchData(anyString())).thenReturn("raw match");
        matchFillingService.getRawMatchData(matchId);
        assertNotNull(redisService.keys(matchId + "raw"));
        assertEquals(1, redisService.keys(matchId + "raw").size());
        assertEquals("raw match", redisService.get(matchId + "raw", String.class).get());
    }

    @Test
    @Order(2)
    public void testGetExistentMatchFromCache() {
        final String matchId = "EUW1_" + UUID.randomUUID();

        final List<PlayerStatsDTO> playerStatsDTOList =
                List.of(
                        new PlayerStatsDTO(
                                new RiotId("puuid1", "kung", "foo", 1),
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
                                new RiotId("puuid2", "TaiLung", "nofoo", 2),
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

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        when(riotAdapterService.getMatchData(anyString())).thenReturn(matchStatsDTO);
        matchFillingService.getFilledMatch(matchId);
        assertNotNull(redisService.keys(matchId));
        assertEquals(1, redisService.keys(matchId).size());
        assertEquals(matchStatsDTO, redisService.get(matchId, MatchStatsDTO.class).get());
    }

    @Test
    @Order(3)
    public void testGetExistentPlayerFromCache() {
        final String name = "kung";
        final String tag = "foo";
        final String playerId = String.format("%s#%s", name, tag);

        final AccountDTO accountDTO = new AccountDTO("puuid", "kung", "foo");

        final RegionDTO regionDTO = new RegionDTO("europe", "euw1");

        final SummonerDTO summonerDTO = new SummonerDTO(1, 10L);

        final PlayerQueueStatsDTO queueDTO =
                new PlayerQueueStatsDTO("RANKED_FOO", new Rank(Tier.EMERALD, 1, 5), 0, 30, false);

        final PlayerDTO playerDTO = new PlayerDTO(accountDTO, regionDTO, summonerDTO, Set.of(queueDTO));

        when(riotAdapterService.getPlayerData(anyString(), anyString())).thenReturn(playerDTO);
        playerFillingService.getFilledPlayer(name, tag);
        assertNotNull(redisService.keys(playerId));
        assertEquals(1, redisService.keys(playerId).size());
        assertEquals(playerDTO, redisService.get(playerId, PlayerDTO.class).get());
    }
}
