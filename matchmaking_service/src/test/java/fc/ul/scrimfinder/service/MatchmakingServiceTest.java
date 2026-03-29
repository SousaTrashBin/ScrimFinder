package fc.ul.scrimfinder.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import fc.ul.scrimfinder.client.RankingServiceClient;
import fc.ul.scrimfinder.domain.Match;
import fc.ul.scrimfinder.domain.MatchTicket;
import fc.ul.scrimfinder.domain.Player;
import fc.ul.scrimfinder.domain.Queue;
import fc.ul.scrimfinder.dto.external.PlayerRankingDTO;
import fc.ul.scrimfinder.dto.request.JoinQueueRequest;
import fc.ul.scrimfinder.grpc.MatchResultResponse;
import fc.ul.scrimfinder.grpc.RankingService;
import fc.ul.scrimfinder.repository.*;
import fc.ul.scrimfinder.util.*;
import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestTransaction
public class MatchmakingServiceTest {

    @Inject MatchmakingService matchmakingService;

    @Inject PlayerRepository playerRepository;

    @Inject QueueRepository queueRepository;

    @Inject MatchTicketRepository ticketRepository;

    @Inject LobbyRepository lobbyRepository;

    @Inject MatchRepository matchRepository;

    @InjectMock @RestClient RankingServiceClient rankingServiceClient;

    @InjectMock
    @io.quarkus.grpc.GrpcClient("ranking-service")
    RankingService rankingGrpcService;

    @InjectMock RedisMatchmakingRepository redisRepository;

    @InjectMock DistributedLockService lockService;

    private List<UUID> mockRedisTickets = new ArrayList<>();

    @BeforeEach
    void setup() {
        mockRedisTickets.clear();
        when(lockService.acquireLock(anyString(), any())).thenReturn(true);
        when(redisRepository.removeTicket(any(UUID.class), any(), any(), any(UUID.class)))
                .thenReturn(true);
        when(rankingGrpcService.reportMatchResults(any()))
                .thenReturn(
                        Uni.createFrom()
                                .item(MatchResultResponse.newBuilder().setSuccess(true).setMessage("OK").build()));

        doAnswer(
                        invocation -> {
                            mockRedisTickets.add(invocation.getArgument(3));
                            return null;
                        })
                .when(redisRepository)
                .addTicket(any(), any(), any(), any(), anyInt());

        when(redisRepository.getTickets(any(), any(), any())).thenReturn(mockRedisTickets);
    }

    @Test
    void testJoinQueueWithDifferentRegions() {
        Queue queue = createQueue("Standard Queue", 2, MatchmakingMode.NORMAL);

        Player p1 = createPlayer("PlayerBR");
        Player p2 = createPlayer("PlayerEUW");

        mockRanking(p1.getId(), queue.getId(), Region.BR, 1200);
        mockRanking(p2.getId(), queue.getId(), Region.EUW, 1200);

        matchmakingService.joinQueue(new JoinQueueRequest(p1.getId(), queue.getId(), null));
        matchmakingService.joinQueue(new JoinQueueRequest(p2.getId(), queue.getId(), null));

        verify(redisRepository, times(1))
                .addTicket(eq(queue.getId()), eq(Region.BR), any(), any(UUID.class), eq(1200));
        verify(redisRepository, times(1))
                .addTicket(eq(queue.getId()), eq(Region.EUW), any(), any(UUID.class), eq(1200));
    }

    @Test
    void testJoinQueueSameRegionFormsMatch() {
        Queue queue = createQueue("Quick Queue", 2, MatchmakingMode.NORMAL);

        Player p1 = createPlayer("Player1");
        Player p2 = createPlayer("Player2");

        mockRanking(p1.getId(), queue.getId(), Region.EUW, 1000);
        mockRanking(p2.getId(), queue.getId(), Region.EUW, 1050);

        var t1DTO = matchmakingService.joinQueue(new JoinQueueRequest(p1.getId(), queue.getId(), null));
        var t2DTO = matchmakingService.joinQueue(new JoinQueueRequest(p2.getId(), queue.getId(), null));

        var lobby1 = matchmakingService.getLobbyByTicket(t1DTO.getId());
        var lobby2 = matchmakingService.getLobbyByTicket(t2DTO.getId());

        assertNotNull(lobby1);
        assertNotNull(lobby2);
        assertEquals(lobby1.getId(), lobby2.getId());
        assertEquals(Region.EUW, lobby1.getRegion());

        Match match = matchRepository.findAll().firstResult();
        assertNotNull(match);
        assertEquals(MatchState.PENDING_ACCEPTANCE, match.getState());
    }

    @Test
    void testAcceptMatchTransitionsToInProgress() {
        Queue queue = createQueue("Quick Queue", 2, MatchmakingMode.NORMAL);
        Player p1 = createPlayer("P1");
        Player p2 = createPlayer("P2");
        mockRanking(p1.getId(), queue.getId(), Region.EUW, 1000);
        mockRanking(p2.getId(), queue.getId(), Region.EUW, 1000);

        var t1 = matchmakingService.joinQueue(new JoinQueueRequest(p1.getId(), queue.getId(), null));
        var t2 = matchmakingService.joinQueue(new JoinQueueRequest(p2.getId(), queue.getId(), null));

        Match match = matchRepository.findAll().firstResult();

        matchmakingService.acceptMatch(match.getId(), p1.getId());
        match = matchRepository.findById(match.getId());
        assertEquals(MatchState.PENDING_ACCEPTANCE, match.getState());
        assertTrue(match.getAcceptedPlayerIds().contains(p1.getId()));

        matchmakingService.acceptMatch(match.getId(), p2.getId());
        match = matchRepository.findById(match.getId());
        assertEquals(MatchState.IN_PROGRESS, match.getState());
    }

    @Test
    void testDeclineMatchCancelsMatch() {
        Queue queue = createQueue("Quick Queue", 2, MatchmakingMode.NORMAL);
        Player p1 = createPlayer("P1");
        Player p2 = createPlayer("P2");
        mockRanking(p1.getId(), queue.getId(), Region.EUW, 1000);
        mockRanking(p2.getId(), queue.getId(), Region.EUW, 1000);

        var t1 = matchmakingService.joinQueue(new JoinQueueRequest(p1.getId(), queue.getId(), null));
        var t2 = matchmakingService.joinQueue(new JoinQueueRequest(p2.getId(), queue.getId(), null));

        Match match = matchRepository.findAll().firstResult();

        matchmakingService.declineMatch(match.getId(), p1.getId());

        match = matchRepository.findById(match.getId());
        assertEquals(MatchState.CANCELLED, match.getState());

        MatchTicket mt1 = ticketRepository.findById(t1.getId());
        MatchTicket mt2 = ticketRepository.findById(t2.getId());

        assertEquals(TicketStatus.CANCELLED, mt1.getStatus());
        assertEquals(TicketStatus.IN_QUEUE, mt2.getStatus());
        assertNull(mt2.getLobby());
        verify(redisRepository, atLeastOnce())
                .addTicket(eq(queue.getId()), eq(Region.EUW), any(), eq(mt2.getId()), anyInt());
    }

    @Test
    void testCompleteMatchReportsResults() {
        Queue queue = createQueue("Quick Queue", 2, MatchmakingMode.NORMAL);
        Player p1 = createPlayer("P1");
        Player p2 = createPlayer("P2");
        mockRanking(p1.getId(), queue.getId(), Region.EUW, 1000);
        mockRanking(p2.getId(), queue.getId(), Region.EUW, 1000);

        var t1 = matchmakingService.joinQueue(new JoinQueueRequest(p1.getId(), queue.getId(), null));
        var t2 = matchmakingService.joinQueue(new JoinQueueRequest(p2.getId(), queue.getId(), null));

        Match match = matchRepository.findAll().firstResult();
        matchmakingService.acceptMatch(match.getId(), p1.getId());
        matchmakingService.acceptMatch(match.getId(), p2.getId());

        matchmakingService.linkMatch(match.getId(), "GAME_123");
        matchmakingService.completeMatch(match.getId());

        match = matchRepository.findById(match.getId());
        assertEquals(MatchState.COMPLETED, match.getState());
        verify(rankingGrpcService, times(1)).reportMatchResults(any());
    }

    @Test
    void testCompleteMatchReportsFailure() {
        Queue queue = createQueue("Quick Queue", 2, MatchmakingMode.NORMAL);
        Player p1 = createPlayer("P1");
        Player p2 = createPlayer("P2");
        mockRanking(p1.getId(), queue.getId(), Region.EUW, 1000);
        mockRanking(p2.getId(), queue.getId(), Region.EUW, 1000);

        var t1 = matchmakingService.joinQueue(new JoinQueueRequest(p1.getId(), queue.getId(), null));
        var t2 = matchmakingService.joinQueue(new JoinQueueRequest(p2.getId(), queue.getId(), null));

        Match match = matchRepository.findAll().firstResult();
        matchmakingService.acceptMatch(match.getId(), p1.getId());
        matchmakingService.acceptMatch(match.getId(), p2.getId());
        matchmakingService.linkMatch(match.getId(), "GAME_123");

        // Mock failure
        final UUID finalMatchId = match.getId();
        when(rankingGrpcService.reportMatchResults(any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("gRPC error")));

        assertThrows(
                RuntimeException.class,
                () -> {
                    matchmakingService.completeMatch(finalMatchId);
                });

        match = matchRepository.findById(match.getId());
        assertEquals(MatchState.RESULT_REPORTING_FAILED, match.getState());
    }

    @Test
    void testJoinQueue_RankingServiceDown() {
        Queue queue = createQueue("Quick Queue", 2, MatchmakingMode.NORMAL);
        Player p1 = createPlayer("P1");

        when(rankingServiceClient.getPlayerRanking(any(UUID.class), any(UUID.class)))
                .thenThrow(new RuntimeException("Service Unavailable"));

        assertThrows(
                RuntimeException.class,
                () -> {
                    matchmakingService.joinQueue(new JoinQueueRequest(p1.getId(), queue.getId(), null));
                });
    }

    private Queue createQueue(String name, int requiredPlayers, MatchmakingMode mode) {
        Queue queue = new Queue();
        queue.setName(name);
        queue.setRequiredPlayers(requiredPlayers);
        queue.setMode(mode);
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
