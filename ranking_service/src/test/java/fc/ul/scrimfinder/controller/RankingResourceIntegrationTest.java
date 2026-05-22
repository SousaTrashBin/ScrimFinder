package fc.ul.scrimfinder.controller;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;

import fc.ul.scrimfinder.domain.Player;
import fc.ul.scrimfinder.domain.PlayerRanking;
import fc.ul.scrimfinder.domain.QueueEntity;
import fc.ul.scrimfinder.domain.RiotAccount;
import fc.ul.scrimfinder.repository.PlayerRankingRepository;
import fc.ul.scrimfinder.repository.PlayerRepository;
import fc.ul.scrimfinder.repository.QueueRepository;
import fc.ul.scrimfinder.repository.RiotAccountRepository;
import fc.ul.scrimfinder.util.MMRRuleType;
import fc.ul.scrimfinder.util.Region;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration-heavy")
@QuarkusTest
class RankingResourceIntegrationTest {

    @Inject PlayerRepository playerRepository;
    @Inject QueueRepository queueRepository;
    @Inject PlayerRankingRepository playerRankingRepository;
    @Inject RiotAccountRepository riotAccountRepository;

    private UUID soloQueueId;
    private UUID flexQueueId;
    private UUID emptyQueueId;
    private UUID playerOneId;
    private UUID playerTwoId;
    private UUID playerWithoutAccountId;
    private UUID playerWithoutRankingId;

    @BeforeEach
    @Transactional
    void setUp() {
        playerRankingRepository.deleteAll();
        riotAccountRepository.deleteAll();
        playerRepository.deleteAll();
        queueRepository.deleteAll();

        QueueEntity soloQueue = createQueue("Solo Queue", MMRRuleType.NONE, 900, true);
        QueueEntity flexQueue = createQueue("Flex Queue", MMRRuleType.FLEX_RANK, 1100, true);
        QueueEntity emptyQueue = createQueue("Empty Queue", MMRRuleType.NONE, 1000, true);
        soloQueueId = soloQueue.getId();
        flexQueueId = flexQueue.getId();
        emptyQueueId = emptyQueue.getId();

        Player playerOne = createPlayer("alpha", 1500, 1300);
        Player playerTwo = createPlayer("bravo", 1200, 1250);
        Player playerWithoutAccount = createPlayer("charlie", 1000, 1000);
        Player playerWithoutRanking = createPlayer("delta", 1000, 1000);
        playerOneId = playerOne.getId();
        playerTwoId = playerTwo.getId();
        playerWithoutAccountId = playerWithoutAccount.getId();
        playerWithoutRankingId = playerWithoutRanking.getId();

        addRiotAccount(playerOne, "PUUID-alpha", "Alpha", "EUW", Region.EUW, true);
        addRiotAccount(playerTwo, "PUUID-bravo", "Bravo", "NA", Region.NA, true);

        createRanking(playerOne, soloQueue, 1400, 8, 3);
        createRanking(playerTwo, soloQueue, 1700, 12, 4);
        createRanking(playerOne, flexQueue, 1550, 6, 2);
    }

    @Test
    void getQueueReturnsPersistedQueue() {
        given()
                .when()
                .get("/queue/" + soloQueueId)
                .then()
                .statusCode(200)
                .body("id", is(soloQueueId.toString()))
                .body("name", is("Solo Queue"))
                .body("mmrRuleType", is("NONE"))
                .body("initialMMR", is(900))
                .body("active", is(true));
    }

    @Test
    void getQueueReturnsNotFoundForUnknownQueue() {
        given()
                .when()
                .get("/queue/" + UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("code", is("QUEUE_NOT_FOUND"));
    }

    @Test
    void updateQueuePersistsPartialUpdate() {
        given()
                .contentType("application/json")
                .body(
                        """
                        {
                          "name": "Updated Solo",
                          "initialMMR": 1234,
                          "active": false
                        }
                        """)
                .when()
                .put("/queue/" + soloQueueId)
                .then()
                .statusCode(200)
                .body("id", is(soloQueueId.toString()))
                .body("name", is("Updated Solo"))
                .body("mmrRuleType", is("NONE"))
                .body("initialMMR", is(1234))
                .body("active", is(false));

        given()
                .when()
                .get("/queue/" + soloQueueId)
                .then()
                .statusCode(200)
                .body("name", is("Updated Solo"))
                .body("initialMMR", is(1234))
                .body("active", is(false));
    }

    @Test
    void updateQueueReturnsNotFoundForUnknownQueue() {
        given()
                .contentType("application/json")
                .body("{\"name\":\"Missing\"}")
                .when()
                .put("/queue/" + UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("code", is("QUEUE_NOT_FOUND"));
    }

    @Test
    void deleteQueueRemovesPersistedQueue() {
        given().when().delete("/queue/" + emptyQueueId).then().statusCode(204);

        given()
                .when()
                .get("/queue/" + emptyQueueId)
                .then()
                .statusCode(404)
                .body("code", is("QUEUE_NOT_FOUND"));
    }

    @Test
    void getPlayerReturnsPersistedPlayerWithAccountAndMmr() {
        given()
                .when()
                .get("/players/" + playerOneId)
                .then()
                .statusCode(200)
                .body("id", is(playerOneId.toString()))
                .body("discordUsername", is("alpha"))
                .body("soloqMMR", is(1500))
                .body("flexMMR", is(1300))
                .body("riotAccounts[0].puuid", is("PUUID-alpha"))
                .body("riotAccounts[0].region", is("EUW"))
                .body("riotAccounts[0].isPrimary", is(true));
    }

    @Test
    void getPlayerReturnsNotFoundForUnknownPlayer() {
        given()
                .when()
                .get("/players/" + UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("code", is("PLAYER_NOT_FOUND"));
    }

    @Test
    void getPrimaryAccountReturnsLinkedPrimaryAccount() {
        given()
                .when()
                .get("/players/" + playerTwoId + "/primary-account")
                .then()
                .statusCode(200)
                .body("puuid", is("PUUID-bravo"))
                .body("gameName", is("Bravo"))
                .body("tagLine", is("NA"))
                .body("region", is("NA"))
                .body("isPrimary", is(true));
    }

    @Test
    void getPrimaryAccountReturnsBadRequestWhenPlayerHasNoLinkedAccount() {
        given()
                .when()
                .get("/players/" + playerWithoutAccountId + "/primary-account")
                .then()
                .statusCode(400)
                .body("code", is("LEAGUE_ACCOUNT_NOT_LINKED"));
    }

    @Test
    void getPlayerQueueRankingsReturnsAllPersistedRankings() {
        given()
                .when()
                .get("/players/" + playerOneId + "/queue-rankings")
                .then()
                .statusCode(200)
                .body("size()", is(2))
                .body("queueId", hasItems(soloQueueId.toString(), flexQueueId.toString()))
                .body("mmr", hasItems(1400, 1550));
    }

    @Test
    void getPlayerQueueRankingByQueueReturnsSingleRanking() {
        given()
                .when()
                .get("/players/" + playerOneId + "/queue-rankings/" + flexQueueId)
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].playerId", is(playerOneId.toString()))
                .body("[0].queueId", is(flexQueueId.toString()))
                .body("[0].mmr", is(1550))
                .body("[0].wins", is(6))
                .body("[0].losses", is(2));
    }

    @Test
    void getPlayerQueueRankingByQueueReturnsNotFoundWhenRankingIsMissing() {
        given()
                .when()
                .get("/players/" + playerWithoutRankingId + "/queue-rankings/" + soloQueueId)
                .then()
                .statusCode(404)
                .body("code", is("PLAYER_RANKING_NOT_FOUND"));
    }

    @Test
    void globalLeaderboardReturnsRankingsOrderedByMmr() {
        given()
                .queryParam("page", 0)
                .queryParam("size", 3)
                .when()
                .get("/leaderboards")
                .then()
                .statusCode(200)
                .body("currentPage", is(0))
                .body("totalElements", equalTo(3))
                .body("data[0].playerId", is(playerTwoId.toString()))
                .body("data[0].mmr", is(1700))
                .body("data[1].playerId", is(playerOneId.toString()))
                .body("data[1].mmr", is(1550))
                .body("data[2].mmr", is(1400));
    }

    @Test
    void queueLeaderboardFiltersByQueue() {
        given()
                .queryParam("page", 0)
                .queryParam("size", 10)
                .when()
                .get("/leaderboards/queues/" + flexQueueId)
                .then()
                .statusCode(200)
                .body("totalElements", equalTo(1))
                .body("data[0].queueId", is(flexQueueId.toString()))
                .body("data[0].playerId", is(playerOneId.toString()))
                .body("data[0].mmr", is(1550));
    }

    @Test
    void globalLeaderboardFiltersByRegion() {
        given()
                .queryParam("region", "NA")
                .when()
                .get("/leaderboards")
                .then()
                .statusCode(200)
                .body("totalElements", equalTo(1))
                .body("data[0].playerId", is(playerTwoId.toString()))
                .body("data[0].region", is("NA"))
                .body("data[0].mmr", is(1700));
    }

    @Test
    void queueLeaderboardReturnsNotFoundForUnknownQueue() {
        given()
                .when()
                .get("/leaderboards/queues/" + UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("code", is("QUEUE_NOT_FOUND"));
    }

    private QueueEntity createQueue(String name, MMRRuleType rule, int initialMmr, boolean active) {
        QueueEntity queue = new QueueEntity();
        queue.setName(name);
        queue.setMmrRuleType(rule);
        queue.setInitialMMR(initialMmr);
        queue.setActive(active);
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

    private void addRiotAccount(
            Player player, String puuid, String gameName, String tagLine, Region region, boolean isPrimary) {
        RiotAccount account = new RiotAccount();
        account.setPlayer(player);
        account.setPuuid(puuid);
        account.setGameName(gameName);
        account.setTagLine(tagLine);
        account.setRegion(region);
        account.setPrimary(isPrimary);
        player.getRiotAccounts().add(account);
        playerRepository.persist(player);
    }

    private void createRanking(Player player, QueueEntity queue, int mmr, int wins, int losses) {
        PlayerRanking ranking = new PlayerRanking();
        ranking.setPlayer(player);
        ranking.setQueue(queue);
        ranking.setMmr(mmr);
        ranking.setWins(wins);
        ranking.setLosses(losses);
        playerRankingRepository.persist(ranking);
    }
}
