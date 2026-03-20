package fc.ul.scrimfinder.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fc.ul.scrimfinder.domain.*;
import fc.ul.scrimfinder.dto.external.ExternalGameDTO;
import fc.ul.scrimfinder.dto.request.CreatePlayerRequest;
import fc.ul.scrimfinder.dto.request.MatchResultRequest;
import fc.ul.scrimfinder.exception.LeagueAccountNotLinkedException;
import fc.ul.scrimfinder.exception.MMRAlreadyExistsException;
import fc.ul.scrimfinder.grpc.MatchHistoryService;
import fc.ul.scrimfinder.grpc.SaveMatchMMRGainsResponse;
import fc.ul.scrimfinder.repository.*;
import fc.ul.scrimfinder.rest.client.ExternalGameClient;
import fc.ul.scrimfinder.util.*;
import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import java.util.*;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestTransaction
public class PlayerRankingServiceTest {

    @Inject PlayerRankingService playerRankingService;
    @Inject PlayerRepository playerRepository;
    @Inject QueueRepository queueRepository;
    @Inject PlayerRankingRepository playerRankingRepository;

    @InjectMock
    @io.quarkus.grpc.GrpcClient("history-service")
    MatchHistoryService matchHistoryService;

    @InjectMock @RestClient ExternalGameClient externalGameClient;

    @Test
    void testProcessMatchResultsSuccess() {
        QueueEntity queue = createQueue(1L, "Standard", MMRRuleType.NONE, 1000);
        Player p1 = createPlayer(100L, "Winner", 1200, 1200);
        Player p2 = createPlayer(200L, "Loser", 1200, 1200);

        createRanking(p1, queue, 1000);
        createRanking(p2, queue, 1000);

        Map<Long, MatchResultRequest.PlayerDelta> deltas = new HashMap<>();
        deltas.put(100L, new MatchResultRequest.PlayerDelta(25, 20));
        deltas.put(200L, new MatchResultRequest.PlayerDelta(25, 20));

        MatchResultRequest request = new MatchResultRequest("GAME_1", 1L, deltas);

        when(externalGameClient.fetchMatchResult("GAME_1"))
                .thenReturn(new ExternalGameDTO("GAME_1", List.of(100L), List.of(200L)));

        when(matchHistoryService.saveMatchMMRGains(any()))
                .thenReturn(
                        Uni.createFrom().item(SaveMatchMMRGainsResponse.newBuilder().setSuccess(true).build()));

        playerRankingService.processMatchResults(request);

        PlayerRanking r1 = playerRankingRepository.findByPlayerAndQueue(p1, queue).orElseThrow();
        PlayerRanking r2 = playerRankingRepository.findByPlayerAndQueue(p2, queue).orElseThrow();

        assertEquals(1025, r1.getMmr());
        assertEquals(1, r1.getWins());
        assertEquals(980, r2.getMmr());
        assertEquals(1, r2.getLosses());

        verify(matchHistoryService).saveMatchMMRGains(any());
    }

    @Test
    void testPopulatePlayerMMRSoloQ() {
        QueueEntity queue = createQueue(1L, "Ranked", MMRRuleType.SOLOQ_RANK, 1000);
        Player player = createPlayer(100L, "User1", 1500, 1200);
        addRiotAccount(player, Region.EUW, true);

        playerRankingService.populatePlayerMMR(100L, new CreatePlayerRequest(Optional.of(1L)));

        PlayerRanking ranking =
                playerRankingRepository.findByPlayerAndQueue(player, queue).orElseThrow();
        assertEquals(1500, ranking.getMmr());
    }

    @Test
    void testPopulatePlayerMMRFlex() {
        QueueEntity queue = createQueue(2L, "Flex", MMRRuleType.FLEX_RANK, 1000);
        Player player = createPlayer(100L, "User1", 1500, 1300);
        addRiotAccount(player, Region.EUW, true);

        playerRankingService.populatePlayerMMR(100L, new CreatePlayerRequest(Optional.of(2L)));

        PlayerRanking ranking =
                playerRankingRepository.findByPlayerAndQueue(player, queue).orElseThrow();
        assertEquals(1300, ranking.getMmr());
    }

    @Test
    void testPopulatePlayerMMR_NoRiotAccount() {
        createQueue(1L, "Q1", MMRRuleType.NONE, 1000);
        createPlayer(1L, "P1", 1200, 1200);

        assertThrows(
                LeagueAccountNotLinkedException.class,
                () -> {
                    playerRankingService.populatePlayerMMR(1L, new CreatePlayerRequest(Optional.of(1L)));
                });
    }

    @Test
    void testPopulatePlayerMMR_AlreadyExists() {
        createQueue(1L, "Q1", MMRRuleType.NONE, 1000);
        Player player = createPlayer(1L, "P1", 1200, 1200);
        addRiotAccount(player, Region.EUW, true);

        playerRankingService.populatePlayerMMR(1L, new CreatePlayerRequest(Optional.of(1L)));

        assertThrows(
                MMRAlreadyExistsException.class,
                () -> {
                    playerRankingService.populatePlayerMMR(1L, new CreatePlayerRequest(Optional.of(1L)));
                });
    }

    private QueueEntity createQueue(Long id, String name, MMRRuleType rule, int initialMmr) {
        QueueEntity queue = new QueueEntity();
        queue.setId(id);
        queue.setName(name);
        queue.setMmrRuleType(rule);
        queue.setInitialMMR(initialMmr);
        queueRepository.persist(queue);
        return queue;
    }

    private Player createPlayer(Long id, String username, int soloMmr, int flexMmr) {
        Player player = new Player();
        player.setId(id);
        player.setDiscordUsername(username);
        player.setSoloqMMR(soloMmr);
        player.setFlexMMR(flexMmr);
        playerRepository.persist(player);
        return player;
    }

    private void addRiotAccount(Player player, Region region, boolean isPrimary) {
        RiotAccount account = new RiotAccount();
        account.setPlayer(player);
        account.setRegion(region);
        account.setPrimary(isPrimary);
        account.setPuuid("PPUID-" + player.getId());
        account.setGameName("Game-" + player.getId());
        account.setTagLine("TAG");
        player.getRiotAccounts().add(account);
        playerRepository.persist(player);
    }

    private void createRanking(Player player, QueueEntity queue, int mmr) {
        PlayerRanking ranking = new PlayerRanking();
        ranking.setPublicId(UUID.randomUUID());
        ranking.setPlayer(player);
        ranking.setQueue(queue);
        ranking.setMmr(mmr);
        playerRankingRepository.persist(ranking);
    }
}
