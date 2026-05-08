package fc.ul.scrimfinder.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import fc.ul.scrimfinder.dto.response.PlayerDTO;
import fc.ul.scrimfinder.exception.PlayerAlreadyExistsException;
import fc.ul.scrimfinder.grpc.RegisterPlayerResponse;
import fc.ul.scrimfinder.repository.PlayerRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestTransaction
public class PlayerServiceTest {

    @Inject PlayerService playerService;

    @Inject PlayerRepository playerRepository;

    @InjectMock
    @io.quarkus.grpc.GrpcClient("ranking-service")
    fc.ul.scrimfinder.grpc.RankingService rankingGrpcClient;

    @BeforeEach
    void setup() {
        when(rankingGrpcClient.registerPlayer(any()))
                .thenReturn(
                        Uni.createFrom().item(RegisterPlayerResponse.newBuilder().setSuccess(true).build()));
    }

    @Test
    void testCreatePlayerGeneratesIdAndRegisters() {
        String username = "TestUser";

        PlayerDTO player = playerService.createPlayer(null, username);

        assertNotNull(player.getId());
        assertEquals(username, player.getDiscordUsername());

        // Verify it was persisted
        assertTrue(playerRepository.findByIdOptional(player.getId()).isPresent());

        // Verify ranking service was called
        verify(rankingGrpcClient, times(1)).registerPlayer(any());
    }

    @Test
    void testCreatePlayerWithSpecificId() {
        String username = "SpecificIdUser";
        java.util.UUID id = java.util.UUID.randomUUID();

        PlayerDTO player = playerService.createPlayer(id, username);

        assertEquals(id, player.getId());
        assertEquals(username, player.getDiscordUsername());

        // Verify it was persisted with exact ID
        assertTrue(playerRepository.findByIdOptional(id).isPresent());
        verify(rankingGrpcClient, times(1)).registerPlayer(any());
    }

    @Test
    void testCreatePlayerDuplicateUsernameThrowsException() {
        String username = "DuplicateUser";
        playerService.createPlayer(null, username);

        assertThrows(
                PlayerAlreadyExistsException.class,
                () -> {
                    playerService.createPlayer(null, username);
                });
    }

    @Test
    void testCreatePlayerNullUsernameThrowsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    playerService.createPlayer(null, null);
                });
    }

    @Test
    void testCreatePlayerBlankUsernameThrowsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    playerService.createPlayer(null, "   ");
                });
    }
}
