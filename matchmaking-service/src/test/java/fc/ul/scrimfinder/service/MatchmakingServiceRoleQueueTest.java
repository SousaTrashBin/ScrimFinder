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
import fc.ul.scrimfinder.repository.*;
import fc.ul.scrimfinder.util.*;
import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestTransaction
public class MatchmakingServiceRoleQueueTest {

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
        when(redisRepository.removeTicket(any(UUID.class), any(), any(), any(UUID.class)))
                .thenReturn(true);
    }

    @Test
    void testRoleQueueMatchFormation() {
        Queue queue = createRoleQueue("Role Queue", MatchmakingMode.NORMAL);

        // Create 10 players, 2 for each role
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            players.add(createPlayer("Player" + i));
        }

        Role[] roles = {Role.TOP, Role.JUNGLE, Role.MID, Role.BOTTOM, Role.SUPPORT};
        List<UUID> ticketIds = new ArrayList<>();

        Map<Role, List<UUID>> currentTickets = new HashMap<>();
        for (Role r : roles) currentTickets.put(r, new ArrayList<>());

        doAnswer(
                        invocation -> {
                            Role r = invocation.getArgument(2);
                            UUID tid = invocation.getArgument(3);
                            currentTickets.get(r).add(tid);
                            return null;
                        })
                .when(redisRepository)
                .addTicket(eq(queue.getId()), any(), any(), any(UUID.class), anyInt());

        when(redisRepository.getTickets(eq(queue.getId()), eq(Region.EUW), any(Role.class)))
                .thenAnswer(
                        invocation -> {
                            Role r = invocation.getArgument(2);
                            return new ArrayList<>(currentTickets.getOrDefault(r, List.of()));
                        });

        for (int i = 0; i < 10; i++) {
            Role role = roles[i / 2];
            Player p = players.get(i);
            mockRanking(p.getId(), queue.getId(), Region.EUW, 1000 + i);
            var ticket =
                    matchmakingService.joinQueue(new JoinQueueRequest(p.getId(), queue.getId(), role));
            ticketIds.add(ticket.getId());
        }

        Match match = matchRepository.findAll().firstResult();
        assertNotNull(match, "Match should have been formed");
        assertEquals(MatchState.PENDING_ACCEPTANCE, match.getState());

        List<MatchTicket> matchedTickets =
                ticketRepository.findAll().list().stream()
                        .filter(t -> t.getStatus() == TicketStatus.MATCHED)
                        .collect(Collectors.toList());

        assertEquals(10, matchedTickets.size());

        Map<Integer, List<MatchTicket>> teams =
                matchedTickets.stream().collect(Collectors.groupingBy(MatchTicket::getTeam));

        assertEquals(2, teams.size());
        assertEquals(5, teams.get(1).size());
        assertEquals(5, teams.get(2).size());

        for (int teamId : new int[] {1, 2}) {
            Set<Role> teamRoles =
                    teams.get(teamId).stream().map(MatchTicket::getRole).collect(Collectors.toSet());
            assertEquals(5, teamRoles.size());
            assertTrue(teamRoles.containsAll(Arrays.asList(roles)));
        }
    }

    @Test
    void testRoleQueueRankBasedMatchmaking() {
        Queue queue = createRoleQueue("Ranked Role Queue", MatchmakingMode.RANK_BASED);
        queue.setMmrWindow(200);
        queueRepository.persist(queue);

        Role[] roles = {Role.TOP, Role.JUNGLE, Role.MID, Role.BOTTOM, Role.SUPPORT};
        List<UUID> ticketIds = new ArrayList<>();

        Map<Role, List<UUID>> currentTickets = new HashMap<>();
        for (Role r : roles) currentTickets.put(r, new ArrayList<>());

        doAnswer(
                        invocation -> {
                            Role r = invocation.getArgument(2);
                            UUID tid = invocation.getArgument(3);
                            currentTickets.get(r).add(tid);
                            return null;
                        })
                .when(redisRepository)
                .addTicket(eq(queue.getId()), any(), any(), any(UUID.class), anyInt());

        when(redisRepository.getTickets(eq(queue.getId()), eq(Region.EUW), any(Role.class)))
                .thenAnswer(
                        invocation -> {
                            Role r = invocation.getArgument(2);
                            return new ArrayList<>(currentTickets.getOrDefault(r, List.of()));
                        });

        for (int i = 0; i < 10; i++) {
            Player p = createPlayer("P" + i);
            Role role = roles[i / 2];
            int mmr = 1000 + (i * 50);
            mockRanking(p.getId(), queue.getId(), Region.EUW, mmr);
            var ticket =
                    matchmakingService.joinQueue(new JoinQueueRequest(p.getId(), queue.getId(), role));
            ticketIds.add(ticket.getId());
        }

        Match match = matchRepository.findAll().firstResult();
        assertNull(match, "Match should NOT have been formed due to MMR window");
    }

    private Queue createRoleQueue(String name, MatchmakingMode mode) {
        Queue queue = new Queue();
        queue.setName(name);
        queue.setRequiredPlayers(10);
        queue.setMode(mode);
        queue.setRoleQueue(true);
        queueRepository.persist(queue);
        return queue;
    }

    private Player createPlayer(String username) {
        Player player = new Player();
        player.setDiscordUsername(username);
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
