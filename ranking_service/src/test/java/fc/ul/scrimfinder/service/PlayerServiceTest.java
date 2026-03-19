package fc.ul.scrimfinder.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import fc.ul.scrimfinder.domain.Player;
import fc.ul.scrimfinder.dto.response.PlayerDTO;
import fc.ul.scrimfinder.exception.PlayerAlreadyCreatedException;
import fc.ul.scrimfinder.exception.PlayerNotFoundException;
import fc.ul.scrimfinder.grpc.ExternalPlayerFillingService;
import fc.ul.scrimfinder.mapper.PlayerMapper;
import fc.ul.scrimfinder.repository.PlayerRepository;
import fc.ul.scrimfinder.util.MMRConverter;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
public class PlayerServiceTest {

    @Inject PlayerService playerService;

    @InjectMock PlayerRepository playerRepository;

    @InjectMock PlayerMapper playerMapper;

    @InjectMock MMRConverter mmrConverter;

    @InjectMock
    @GrpcClient("player-service")
    ExternalPlayerFillingService playerFillingClient;

    @Test
    public void testCreatePlayer_Success() {
        Long id = 1L;
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
        Long id = 1L;
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
        when(playerRepository.findByIdOptional(anyLong())).thenReturn(Optional.empty());

        assertThrows(
                PlayerNotFoundException.class,
                () -> {
                    playerService.syncPlayerMMR(1L);
                });
    }
}
