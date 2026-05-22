package fc.ul.scrimfinder.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import fc.ul.scrimfinder.exception.PlayerNotFoundException;
import fc.ul.scrimfinder.exception.QueueNotFoundException;
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
import java.util.UUID;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration-heavy")
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

    @Test
    void testPopulatePlayerMMRDefaultRuleUsesQueueInitialMMR() {
        QueueEntity queue = createQueue("Default Queue", MMRRuleType.NONE, 875);
        Player player = createPlayer("DefaultRuleUser", 1700, 1600);
        addRiotAccount(player, Region.EUW, true);

        var dto =
                playerRankingService.populatePlayerMMR(
                        player.getId(), new CreatePlayerRequest(Optional.of(queue.getId())));

        PlayerRanking ranking =
                playerRankingRepository.findByPlayerAndQueue(player, queue).orElseThrow();
        assertEquals(875, ranking.getMmr());
        assertEquals(875, dto.mmr());
    }

    @Test
    void testPopulatePlayerMMRReturnsMappedRankingDTO() {
        QueueEntity queue = createQueue("Mapped Queue", MMRRuleType.NONE, 1111);
        Player player = createPlayer("MappedUser", 1400, 1300);
        addRiotAccount(player, Region.NA, true);

        var dto =
                playerRankingService.populatePlayerMMR(
                        player.getId(), new CreatePlayerRequest(Optional.of(queue.getId())));

        assertNotNull(dto.id());
        assertEquals(player.getId(), dto.playerId());
        assertEquals("MappedUser", dto.discordUsername());
        assertEquals(player.getPrimaryAccount().getPuuid(), dto.lolAccountPPUID());
        assertEquals(Region.NA, dto.region());
        assertEquals(queue.getId(), dto.queueId());
        assertEquals(1111, dto.mmr());
        assertEquals(0, dto.wins());
        assertEquals(0, dto.losses());
    }

    @Test
    void testPopulatePlayerMMRAllowsLinkedNonPrimaryRiotAccount() {
        QueueEntity queue = createQueue("Non Primary Account Queue", MMRRuleType.NONE, 1000);
        Player player = createPlayer("NonPrimaryUser", 1200, 1200);
        addRiotAccount(player, Region.EUW, false);

        var dto =
                playerRankingService.populatePlayerMMR(
                        player.getId(), new CreatePlayerRequest(Optional.of(queue.getId())));

        assertEquals(player.getId(), dto.playerId());
        assertEquals(queue.getId(), dto.queueId());
        assertEquals(1000, dto.mmr());
        assertNull(dto.lolAccountPPUID());
        assertNull(dto.region());
    }

    @Test
    void testPopulatePlayerMMRThrowsWhenPlayerDoesNotExist() {
        QueueEntity queue = createQueue("Missing Player Queue", MMRRuleType.NONE, 1000);

        assertThrows(
                PlayerNotFoundException.class,
                () ->
                        playerRankingService.populatePlayerMMR(
                                UUID.randomUUID(), new CreatePlayerRequest(Optional.of(queue.getId()))));
    }

    @Test
    void testPopulatePlayerMMRThrowsWhenQueueIdIsEmpty() {
        Player player = createPlayer("MissingQueueUser", 1200, 1200);
        addRiotAccount(player, Region.EUW, true);

        assertThrows(
                QueueNotFoundException.class,
                () ->
                        playerRankingService.populatePlayerMMR(
                                player.getId(), new CreatePlayerRequest(Optional.empty())));
    }

    @Test
    void testPopulatePlayerMMRThrowsWhenQueueDoesNotExist() {
        Player player = createPlayer("UnknownQueueUser", 1200, 1200);
        addRiotAccount(player, Region.EUW, true);

        assertThrows(
                QueueNotFoundException.class,
                () ->
                        playerRankingService.populatePlayerMMR(
                                player.getId(), new CreatePlayerRequest(Optional.of(UUID.randomUUID()))));
    }

    @Test
    void testProcessMatchResultsAppliesEachPlayersOwnDelta() {
        QueueEntity queue = createQueue("Variable Delta Queue", MMRRuleType.NONE, 1000);
        Player winner = createPlayer("VariableWinner", 1200, 1200);
        Player loser = createPlayer("VariableLoser", 1200, 1200);
        addRiotAccount(winner, Region.EUW, true);
        addRiotAccount(loser, Region.EUW, true);
        createRanking(winner, queue, 1000);
        createRanking(loser, queue, 1000);

        String winnerPuuid = winner.getPrimaryAccount().getPuuid();
        String loserPuuid = loser.getPrimaryAccount().getPuuid();

        Map<String, MatchResultRequest.PlayerDelta> deltas = new HashMap<>();
        deltas.put(winnerPuuid, new MatchResultRequest.PlayerDelta(31, 9));
        deltas.put(loserPuuid, new MatchResultRequest.PlayerDelta(45, 17));

        when(externalGameClient.fetchMatchResult("GAME_VARIABLE_DELTAS"))
                .thenReturn(
                        new ExternalGameDTO(
                                "GAME_VARIABLE_DELTAS",
                                List.of(
                                        new ExternalPlayerStatsDTO(
                                                new ExternalRiotId(winnerPuuid, "Winner", "TAG"), true),
                                        new ExternalPlayerStatsDTO(
                                                new ExternalRiotId(loserPuuid, "Loser", "TAG"), false))));

        var results =
                playerRankingService.processMatchResults(
                        new MatchResultRequest("GAME_VARIABLE_DELTAS", queue.getId(), deltas));

        PlayerRanking winnerRanking =
                playerRankingRepository.findByPlayerAndQueue(winner, queue).orElseThrow();
        PlayerRanking loserRanking =
                playerRankingRepository.findByPlayerAndQueue(loser, queue).orElseThrow();
        assertEquals(1031, winnerRanking.getMmr());
        assertEquals(1, winnerRanking.getWins());
        assertEquals(983, loserRanking.getMmr());
        assertEquals(1, loserRanking.getLosses());
        assertEquals(2, results.size());
        assertEquals(1031, results.get(winner.getId()).mmr());
        assertEquals(983, results.get(loser.getId()).mmr());
    }

    @Test
    void testProcessMatchResultsSkipsPlayerMissingFromExternalResult() {
        QueueEntity queue = createQueue("Skip Missing External Queue", MMRRuleType.NONE, 1000);
        Player present = createPlayer("PresentInExternal", 1200, 1200);
        Player missing = createPlayer("MissingFromExternal", 1200, 1200);
        addRiotAccount(present, Region.EUW, true);
        addRiotAccount(missing, Region.EUW, true);
        createRanking(present, queue, 1000);
        createRanking(missing, queue, 1000);

        String presentPuuid = present.getPrimaryAccount().getPuuid();
        String missingPuuid = missing.getPrimaryAccount().getPuuid();

        Map<String, MatchResultRequest.PlayerDelta> deltas = new HashMap<>();
        deltas.put(presentPuuid, new MatchResultRequest.PlayerDelta(20, 20));
        deltas.put(missingPuuid, new MatchResultRequest.PlayerDelta(20, 20));

        when(externalGameClient.fetchMatchResult("GAME_SKIP_MISSING"))
                .thenReturn(
                        new ExternalGameDTO(
                                "GAME_SKIP_MISSING",
                                List.of(
                                        new ExternalPlayerStatsDTO(
                                                new ExternalRiotId(presentPuuid, "Present", "TAG"), true))));

        var results =
                playerRankingService.processMatchResults(
                        new MatchResultRequest("GAME_SKIP_MISSING", queue.getId(), deltas));

        PlayerRanking presentRanking =
                playerRankingRepository.findByPlayerAndQueue(present, queue).orElseThrow();
        PlayerRanking missingRanking =
                playerRankingRepository.findByPlayerAndQueue(missing, queue).orElseThrow();
        assertEquals(1020, presentRanking.getMmr());
        assertEquals(1, presentRanking.getWins());
        assertEquals(1000, missingRanking.getMmr());
        assertEquals(0, missingRanking.getWins());
        assertEquals(0, missingRanking.getLosses());
        assertEquals(1, results.size());
        assertTrue(results.containsKey(present.getId()));
        assertFalse(results.containsKey(missing.getId()));
    }

    @Test
    void testProcessMatchResultsThrowsWhenQueueDoesNotExist() {
        Map<String, MatchResultRequest.PlayerDelta> deltas = new HashMap<>();
        deltas.put("UNKNOWN-PUUID", new MatchResultRequest.PlayerDelta(20, 20));

        assertThrows(
                QueueNotFoundException.class,
                () ->
                        playerRankingService.processMatchResults(
                                new MatchResultRequest("GAME_UNKNOWN_QUEUE", UUID.randomUUID(), deltas)));

        verify(externalGameClient, never()).fetchMatchResult("GAME_UNKNOWN_QUEUE");
    }

    @Test
    void testProcessMatchResultsThrowsWhenRiotAccountDoesNotExist() {
        QueueEntity queue = createQueue("Missing Riot Account Queue", MMRRuleType.NONE, 1000);
        String unknownPuuid = "UNKNOWN-PUUID-" + UUID.randomUUID();
        Map<String, MatchResultRequest.PlayerDelta> deltas = new HashMap<>();
        deltas.put(unknownPuuid, new MatchResultRequest.PlayerDelta(20, 20));

        when(externalGameClient.fetchMatchResult("GAME_UNKNOWN_RIOT_ACCOUNT"))
                .thenReturn(
                        new ExternalGameDTO(
                                "GAME_UNKNOWN_RIOT_ACCOUNT",
                                List.of(
                                        new ExternalPlayerStatsDTO(
                                                new ExternalRiotId(unknownPuuid, "Unknown", "TAG"), true))));

        assertThrows(
                PlayerNotFoundException.class,
                () ->
                        playerRankingService.processMatchResults(
                                new MatchResultRequest("GAME_UNKNOWN_RIOT_ACCOUNT", queue.getId(), deltas)));
    }

    @Test
    void testProcessMatchResultsThrowsWhenRankingRecordIsMissing() {
        QueueEntity queue = createQueue("Missing Ranking Queue", MMRRuleType.NONE, 1000);
        Player player = createPlayer("MissingRankingUser", 1200, 1200);
        addRiotAccount(player, Region.EUW, true);
        String puuid = player.getPrimaryAccount().getPuuid();
        Map<String, MatchResultRequest.PlayerDelta> deltas = new HashMap<>();
        deltas.put(puuid, new MatchResultRequest.PlayerDelta(20, 20));

        when(externalGameClient.fetchMatchResult("GAME_MISSING_RANKING"))
                .thenReturn(
                        new ExternalGameDTO(
                                "GAME_MISSING_RANKING",
                                List.of(
                                        new ExternalPlayerStatsDTO(
                                                new ExternalRiotId(puuid, "MissingRanking", "TAG"), true))));

        assertThrows(
                MMRAlreadyExistsException.class,
                () ->
                        playerRankingService.processMatchResults(
                                new MatchResultRequest("GAME_MISSING_RANKING", queue.getId(), deltas)));
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
