package fc.ul.scrimfinder.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import fc.ul.scrimfinder.client.RankingServiceClient;
import fc.ul.scrimfinder.domain.MatchTicket;
import fc.ul.scrimfinder.domain.Player;
import fc.ul.scrimfinder.domain.Queue;
import fc.ul.scrimfinder.dto.external.PlayerRankingDTO;
import fc.ul.scrimfinder.dto.request.JoinQueueRequest;
import fc.ul.scrimfinder.exception.LeagueAccountNotLinkedException;
import fc.ul.scrimfinder.exception.PlayerNotFoundException;
import fc.ul.scrimfinder.exception.QueueNotFoundException;
import fc.ul.scrimfinder.repository.*;
import fc.ul.scrimfinder.util.*;
import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestTransaction
public class MatchmakingServiceEdgeCasesTest {

    @Inject MatchmakingService matchmakingService;
    @Inject PlayerRepository playerRepository;
    @Inject QueueRepository queueRepository;
    @Inject MatchTicketRepository ticketRepository;
    @Inject LobbyRepository lobbyRepository;
    @Inject MatchRepository matchRepository;

    @InjectMock @RestClient RankingServiceClient rankingServiceClient;
    @InjectMock RedisMatchmakingRepository redisRepository;
    @InjectMock DistributedLockService lockService;

    @BeforeEach
    void setup() {
        when(lockService.acquireLock(anyString(), any())).thenReturn(true);
    }

    @Test
    void testJoinQueuePlayerNotFound() {
        UUID pid = UUID.randomUUID();
        UUID qid = UUID.randomUUID();
        assertThrows(
                PlayerNotFoundException.class,
                () -> {
                    matchmakingService.joinQueue(new JoinQueueRequest(pid, qid, null));
                });
    }

    @Test
    void testJoinQueueQueueNotFound() {
        Player p = createPlayer("P1");
        UUID qid = UUID.randomUUID();
        assertThrows(
                QueueNotFoundException.class,
                () -> {
                    matchmakingService.joinQueue(new JoinQueueRequest(p.getId(), qid, null));
                });
    }

    @Test
    void testJoinQueueLeagueAccountNotLinked() {
        Player p = createPlayer("P1");
        Queue q = createQueue("Q1", 2, MatchmakingMode.NORMAL, null);

        when(rankingServiceClient.getPlayerRanking(p.getId(), q.getId()))
                .thenReturn(
                        List.of(
                                new PlayerRankingDTO(null, p.getId(), null, null, null, q.getId(), 1000, 0, 0)));

        assertThrows(
                LeagueAccountNotLinkedException.class,
                () -> {
                    matchmakingService.joinQueue(new JoinQueueRequest(p.getId(), q.getId(), null));
                });
    }

    @Test
    void testJoinQueueRegionMismatch() {
        Player p = createPlayer("P1");
        Queue q = createQueue("Q1", 2, MatchmakingMode.NORMAL, Region.EUW);

        mockRanking(p.getId(), q.getId(), Region.BR, 1000);

        assertThrows(
                RuntimeException.class,
                () -> {
                    matchmakingService.joinQueue(new JoinQueueRequest(p.getId(), q.getId(), null));
                },
                "Should throw exception due to region mismatch");
    }

    @Test
    void testLeaveQueue() {
        Player p = createPlayer("P1");
        Queue q = createQueue("Q1", 2, MatchmakingMode.NORMAL, null);
        mockRanking(p.getId(), q.getId(), Region.EUW, 1000);

        var ticketDTO = matchmakingService.joinQueue(new JoinQueueRequest(p.getId(), q.getId(), null));

        matchmakingService.leaveQueue(ticketDTO.getId());

        MatchTicket ticket = ticketRepository.findById(ticketDTO.getId());
        assertEquals(TicketStatus.CANCELLED, ticket.getStatus());
        verify(redisRepository)
                .removeTicket(eq(q.getId()), eq(Region.EUW), any(), eq(ticketDTO.getId()));
    }

    private Queue createQueue(String name, int required, MatchmakingMode mode, Region region) {
        Queue queue = new Queue();
        queue.setName(name);
        queue.setRequiredPlayers(required);
        queue.setMode(mode);
        queue.setRegion(region);
        queueRepository.persist(queue);
        return queue;
    }

    private Player createPlayer(String username) {
        Player player = new Player();
        player.setUsername(username);
        playerRepository.persist(player);
        return player;
    }

    private void mockRanking(UUID playerId, UUID queueId, Region region, int mmr) {
        when(rankingServiceClient.getPlayerRanking(playerId, queueId))
                .thenReturn(
                        List.of(
                                new PlayerRankingDTO(
                                        UUID.randomUUID(),
                                        playerId,
                                        "User" + playerId,
                                        "P" + playerId,
                                        region,
                                        queueId,
                                        mmr,
                                        0,
                                        0)));
    }
}
