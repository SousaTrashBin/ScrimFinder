package fc.ul.scrimfinder.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fc.ul.scrimfinder.domain.Player;
import fc.ul.scrimfinder.domain.PlayerRanking;
import fc.ul.scrimfinder.domain.QueueEntity;
import fc.ul.scrimfinder.domain.RiotAccount;
import fc.ul.scrimfinder.dto.external.ExternalGameDTO;
import fc.ul.scrimfinder.dto.external.ExternalPlayerStatsDTO;
import fc.ul.scrimfinder.dto.external.ExternalRiotId;
import fc.ul.scrimfinder.dto.request.CreatePlayerRequest;
import fc.ul.scrimfinder.dto.request.MatchResultRequest;
import fc.ul.scrimfinder.exception.LeagueAccountNotLinkedException;
import fc.ul.scrimfinder.exception.MMRAlreadyExistsException;
import fc.ul.scrimfinder.repository.PlayerRankingRepository;
import fc.ul.scrimfinder.repository.PlayerRepository;
import fc.ul.scrimfinder.repository.QueueRepository;
import fc.ul.scrimfinder.rest.client.ExternalGameClient;
import fc.ul.scrimfinder.util.MMRRuleType;
import fc.ul.scrimfinder.util.Region;
import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestTransaction
public class PlayerRankingServiceTest {

    @Inject PlayerRankingService playerRankingService;
    @Inject PlayerRepository playerRepository;
    @Inject QueueRepository queueRepository;
    @Inject PlayerRankingRepository playerRankingRepository;

    @InjectMock @RestClient ExternalGameClient externalGameClient;

    @Test
    void testProcessMatchResultsSuccess() {
        QueueEntity queue = createQueue("Standard", MMRRuleType.NONE, 1000);
        Player p1 = createPlayer("Winner", 1200, 1200);
        Player p2 = createPlayer("Loser", 1200, 1200);
        addRiotAccount(p1, Region.EUW, true);
        addRiotAccount(p2, Region.EUW, true);

        String puuid1 = p1.getPrimaryAccount().getPuuid();
        String puuid2 = p2.getPrimaryAccount().getPuuid();

        createRanking(p1, queue, 1000);
        createRanking(p2, queue, 1000);

        Map<String, MatchResultRequest.PlayerDelta> deltas = new HashMap<>();
        deltas.put(puuid1, new MatchResultRequest.PlayerDelta(25, 20));
        deltas.put(puuid2, new MatchResultRequest.PlayerDelta(25, 20));

        MatchResultRequest request = new MatchResultRequest("GAME_1", queue.getId(), deltas);

        // Update to new DTO structure
        ExternalGameDTO gameResult =
                new ExternalGameDTO(
                        "GAME_1",
                        List.of(
                                new ExternalPlayerStatsDTO(new ExternalRiotId(puuid1, "Winner", "TAG"), true),
                                new ExternalPlayerStatsDTO(new ExternalRiotId(puuid2, "Loser", "TAG"), false)));

        when(externalGameClient.fetchMatchResult("GAME_1")).thenReturn(gameResult);

        playerRankingService.processMatchResults(request);

        PlayerRanking r1 = playerRankingRepository.findByPlayerAndQueue(p1, queue).orElseThrow();
        PlayerRanking r2 = playerRankingRepository.findByPlayerAndQueue(p2, queue).orElseThrow();

        assertEquals(1025, r1.getMmr());
        assertEquals(1, r1.getWins());
        assertEquals(980, r2.getMmr());
        assertEquals(1, r2.getLosses());
    }

    @Test
    void testPopulatePlayerMMRSoloQ() {
        QueueEntity queue = createQueue("Ranked", MMRRuleType.SOLOQ_RANK, 1000);
        Player player = createPlayer("User1", 1500, 1200);
        addRiotAccount(player, Region.EUW, true);

        playerRankingService.populatePlayerMMR(
                player.getId(), new CreatePlayerRequest(Optional.of(queue.getId())));

        PlayerRanking ranking =
                playerRankingRepository.findByPlayerAndQueue(player, queue).orElseThrow();
        assertEquals(1500, ranking.getMmr());
    }

    @Test
    void testPopulatePlayerMMRFlex() {
        QueueEntity queue = createQueue("Flex", MMRRuleType.FLEX_RANK, 1000);
        Player player = createPlayer("User1", 1500, 1300);
        addRiotAccount(player, Region.EUW, true);

        playerRankingService.populatePlayerMMR(
                player.getId(), new CreatePlayerRequest(Optional.of(queue.getId())));

        PlayerRanking ranking =
                playerRankingRepository.findByPlayerAndQueue(player, queue).orElseThrow();
        assertEquals(1300, ranking.getMmr());
    }

    @Test
    void testPopulatePlayerMMR_NoRiotAccount() {
        QueueEntity q = createQueue("Q1", MMRRuleType.NONE, 1000);
        Player p = createPlayer("P1", 1200, 1200);

        assertThrows(
                LeagueAccountNotLinkedException.class,
                () -> {
                    playerRankingService.populatePlayerMMR(
                            p.getId(), new CreatePlayerRequest(Optional.of(q.getId())));
                });
    }

    @Test
    void testPopulatePlayerMMR_AlreadyExists() {
        QueueEntity q = createQueue("Q1", MMRRuleType.NONE, 1000);
        Player player = createPlayer("P1", 1200, 1200);
        addRiotAccount(player, Region.EUW, true);

        playerRankingService.populatePlayerMMR(
                player.getId(), new CreatePlayerRequest(Optional.of(q.getId())));

        assertThrows(
                MMRAlreadyExistsException.class,
                () -> {
                    playerRankingService.populatePlayerMMR(
                            player.getId(), new CreatePlayerRequest(Optional.of(q.getId())));
                });
    }

    private QueueEntity createQueue(String name, MMRRuleType rule, int initialMmr) {
        QueueEntity queue = new QueueEntity();
        queue.setName(name);
        queue.setMmrRuleType(rule);
        queue.setInitialMMR(initialMmr);
        queueRepository.persist(queue);
        return queue;
    }

    private Player createPlayer(String username, int soloMmr, int flexMmr) {
        Player player = new Player();
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
        ranking.setPlayer(player);
        ranking.setQueue(queue);
        ranking.setMmr(mmr);
        playerRankingRepository.persist(ranking);
    }
}
