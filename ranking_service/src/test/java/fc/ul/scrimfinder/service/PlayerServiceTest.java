package fc.ul.scrimfinder.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fc.ul.scrimfinder.domain.Player;
import fc.ul.scrimfinder.domain.RiotAccount;
import fc.ul.scrimfinder.dto.external.ExternalPlayerResponse;
import fc.ul.scrimfinder.dto.response.PlayerDTO;
import fc.ul.scrimfinder.exception.ExternalServiceUnavailableException;
import fc.ul.scrimfinder.exception.PlayerAlreadyCreatedException;
import fc.ul.scrimfinder.exception.PlayerNotFoundException;
import fc.ul.scrimfinder.mapper.PlayerMapper;
import fc.ul.scrimfinder.repository.PlayerRepository;
import fc.ul.scrimfinder.rest.client.ExternalPlayerClient;
import fc.ul.scrimfinder.util.MMRConverter;
import fc.ul.scrimfinder.util.Region;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
@TestTransaction
public class PlayerServiceTest {

    @Inject PlayerService playerService;

    @InjectMock PlayerRepository playerRepository;

    @InjectMock PlayerMapper playerMapper;

    @InjectMock MMRConverter mmrConverter;

    @InjectMock @RestClient ExternalPlayerClient externalPlayerClient;

    @Test
    public void testCreatePlayer_Success() {
        UUID id = UUID.randomUUID();
        String username = "testuser";

        PanacheQuery query = Mockito.mock(PanacheQuery.class);
        when(query.count()).thenReturn(0L);
        when(playerRepository.find("discordUsername", username)).thenReturn(query);

        PlayerDTO expectedDTO = new PlayerDTO(id, username, java.util.List.of(), 0, 0);

        when(playerMapper.toDTO(any(Player.class))).thenReturn(expectedDTO);

        PlayerDTO result = playerService.createPlayer(id, username);

        assertNotNull(result);
        assertEquals(username, result.discordUsername());
        Mockito.verify(playerRepository).persist(any(Player.class));
    }

    @Test
    public void testCreatePlayer_AlreadyExists() {
        UUID id = UUID.randomUUID();
        String username = "existinguser";

        PanacheQuery query = Mockito.mock(PanacheQuery.class);
        when(query.count()).thenReturn(1L);
        when(playerRepository.find("discordUsername", username)).thenReturn(query);

        assertThrows(
                PlayerAlreadyCreatedException.class,
                () -> {
                    playerService.createPlayer(id, username);
                });
    }

    @Test
    public void testSyncPlayerMMR_PlayerNotFound() {
        when(playerRepository.findByIdOptional(any(UUID.class))).thenReturn(Optional.empty());

        assertThrows(
                PlayerNotFoundException.class,
                () -> {
                    playerService.syncPlayerMMR(UUID.randomUUID());
                });
    }

    @Test
    public void testLinkLolAccount_Success() {
        UUID pid = UUID.randomUUID();
        Player p = new Player();
        p.setId(pid);
        p.setDiscordUsername("user");
        when(playerRepository.findByIdOptional(pid)).thenReturn(Optional.of(p));

        when(externalPlayerClient.fetchPlayerRank(anyString(), anyString(), anyString()))
                .thenReturn(
                        new ExternalPlayerResponse(
                                new ExternalPlayerResponse.Account("puuid-123", "Name", "TAG"), null, null, null));

        when(playerMapper.toDTO(any()))
                .thenReturn(new PlayerDTO(pid, "user", java.util.List.of(), 1000, 1000));

        PlayerDTO result = playerService.linkLolAccount(pid, "puuid-123", "Name", "TAG", Region.EUW);

        assertNotNull(result);
        verify(playerRepository, times(1)).persistAndFlush(p);
        verify(playerRepository, times(1)).persist(p);
        assertEquals(1, p.getRiotAccounts().size());
        assertTrue(p.getRiotAccounts().get(0).isPrimary());
    }

    @Test
    public void testLinkLolAccount_ExternalServiceFailure() {
        UUID pid = UUID.randomUUID();
        Player p = new Player();
        p.setId(pid);
        when(playerRepository.findByIdOptional(pid)).thenReturn(Optional.of(p));

        when(externalPlayerClient.fetchPlayerRank(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Service down"));

        assertThrows(
                ExternalServiceUnavailableException.class,
                () -> {
                    playerService.linkLolAccount(pid, "puuid-123", "Name", "TAG", Region.EUW);
                });
    }

    @Test
    public void testSyncPlayerMMR_Success() {
        UUID pid = UUID.randomUUID();
        Player p = new Player();
        p.setId(pid);
        RiotAccount ra = new RiotAccount();
        ra.setPrimary(true);
        ra.setGameName("Name");
        ra.setTagLine("TAG");
        ra.setRegion(Region.EUW);
        p.getRiotAccounts().add(ra);

        when(playerRepository.findByIdOptional(pid)).thenReturn(Optional.of(p));

        when(externalPlayerClient.fetchPlayerRank(anyString(), anyString(), anyString()))
                .thenReturn(
                        new ExternalPlayerResponse(
                                null,
                                null,
                                null,
                                java.util.List.of(
                                        new ExternalPlayerResponse.Queue(
                                                "RANKED_SOLO_5x5",
                                                new ExternalPlayerResponse.Rank("GOLD", 2, 50),
                                                0,
                                                0,
                                                false))));

        when(mmrConverter.convertRankToMMR(any())).thenReturn(1450);

        playerService.syncPlayerMMR(pid);

        assertEquals(1450, p.getSoloqMMR());
        verify(playerRepository).persist(p);
    }
}
