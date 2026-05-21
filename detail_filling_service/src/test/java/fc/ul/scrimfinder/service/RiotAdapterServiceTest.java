package fc.ul.scrimfinder.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import fc.ul.scrimfinder.client.*;
import fc.ul.scrimfinder.dto.response.player.*;
import fc.ul.scrimfinder.exception.InvalidMatchFormatException;
import fc.ul.scrimfinder.exception.InvalidPlayerFormatException;
import fc.ul.scrimfinder.mapper.RiotMapper;
import fc.ul.scrimfinder.util.Rank;
import fc.ul.scrimfinder.util.Tier;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Set;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.*;

@QuarkusTest
@Tag("unit")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RiotAdapterServiceTest {

    @Inject RiotAdapterService riotAdapterService;

    @InjectMock RiotMapper riotMapper;

    @InjectMock @RestClient RiotMatchServiceClient riotMatchServiceClient;

    @InjectMock @RestClient RiotAccountServiceClient riotAccountServiceClient;

    @InjectMock @RestClient RiotSummonerServiceClient riotSummonerServiceClient;

    @InjectMock @RestClient RiotPlayerServiceClient riotPlayerServiceClient;

    @Test
    @Order(1)
    public void testGetRawMatch_ShouldThrowInvalidMatchFormatException_WhenMatchIdHasNoSubregion() {
        final String matchId = "7779779801";
        assertThrows(
                InvalidMatchFormatException.class, () -> riotAdapterService.getRawMatchData(matchId));
    }

    @Test
    @Order(2)
    public void
            testGetRawMatch_ShouldThrowInvalidMatchFormatException_WhenMatchIdHasInvalidSubregion() {
        final String matchId = "EUW_7779779801";
        assertThrows(
                InvalidMatchFormatException.class, () -> riotAdapterService.getRawMatchData(matchId));
    }

    @Test
    @Order(3)
    public void testGetRawMatch_ShouldReturnRawMatch_WhenMatchIdIsValid() {
        final String matchId = "EUW1_7779779801";

        when(riotMatchServiceClient.getMatch(anyString())).thenReturn("raw match");

        assertEquals("raw match", riotAdapterService.getRawMatchData(matchId));
    }

    @Test
    @Order(4)
    public void testGetPlayerData_ShouldThrowInvalidPlayerFormatException_WhenInvalidServer() {
        final String server = "3UW";
        final String name = "kung";
        final String tag = "foo";
        assertThrows(
                InvalidPlayerFormatException.class,
                () -> riotAdapterService.getPlayerData(server, name, tag));
    }

    @Test
    @Order(5)
    public void testGetPlayerData_ShouldReturnPlayer_WhenMatchIdIsValid()
            throws JsonProcessingException {
        final String server = "EUW";
        final String name = "kung";
        final String tag = "foo";

        final String puuid = UUID.randomUUID().toString();

        final AccountDTO accountDTO = new AccountDTO(puuid, "kung", "foo");
        final RegionDTO regionDTO = new RegionDTO("europe", "euw1");
        final SummonerDTO summonerDTO = new SummonerDTO(1, 10L);
        final PlayerQueueStatsDTO queueDTO =
                new PlayerQueueStatsDTO("RANKED_FOO", new Rank(Tier.EMERALD, 1, 5), 0, 30, false);
        final PlayerDTO playerDTO = new PlayerDTO(accountDTO, regionDTO, summonerDTO, Set.of(queueDTO));

        when(riotAccountServiceClient.getByRiotId(anyString(), anyString())).thenReturn("");
        when(riotSummonerServiceClient.getByAccessToken(anyString())).thenReturn("");
        when(riotPlayerServiceClient.getLeagueEntriesByPUUID(anyString())).thenReturn("[{}]");

        when(riotMapper.toAccountDTO(any())).thenReturn(accountDTO);
        when(riotMapper.toSummonerDTO(any())).thenReturn(summonerDTO);
        when(riotMapper.toPlayerQueueStatsDTO(any())).thenReturn(queueDTO);

        assertEquals(playerDTO, riotAdapterService.getPlayerData(server, name, tag));

        verify(riotAccountServiceClient).getByRiotId(anyString(), anyString());
        verify(riotSummonerServiceClient).getByAccessToken(anyString());
        verify(riotPlayerServiceClient).getLeagueEntriesByPUUID(anyString());
    }
}
