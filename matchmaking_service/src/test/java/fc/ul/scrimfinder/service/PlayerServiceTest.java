package fc.ul.scrimfinder.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import fc.ul.scrimfinder.client.RankingServiceClient;
import fc.ul.scrimfinder.dto.response.PlayerDTO;
import fc.ul.scrimfinder.exception.PlayerAlreadyExistsException;
import fc.ul.scrimfinder.repository.PlayerRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestTransaction
public class PlayerServiceTest {

    @Inject PlayerService playerService;

    @Inject PlayerRepository playerRepository;

    @InjectMock @RestClient RankingServiceClient rankingServiceClient;

    @Test
    void testCreatePlayerGeneratesIdAndRegisters() {
        String username = "TestUser";

        PlayerDTO player = playerService.createPlayer(username);

        assertNotNull(player.getId());
        assertEquals(username, player.getUsername());

        // Verify it was persisted
        assertTrue(playerRepository.findByIdOptional(player.getId()).isPresent());

        // Verify ranking service was called
        verify(rankingServiceClient, times(1)).registerPlayer(eq(player.getId()), eq(username));
    }

    @Test
    void testCreatePlayerDuplicateUsernameThrowsException() {
        String username = "DuplicateUser";
        playerService.createPlayer(username);

        assertThrows(
                PlayerAlreadyExistsException.class,
                () -> {
                    playerService.createPlayer(username);
                });
    }

    @Test
    void testCreatePlayerNullUsernameThrowsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    playerService.createPlayer(null);
                });
    }

    @Test
    void testCreatePlayerBlankUsernameThrowsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    playerService.createPlayer("   ");
                });
    }
}
