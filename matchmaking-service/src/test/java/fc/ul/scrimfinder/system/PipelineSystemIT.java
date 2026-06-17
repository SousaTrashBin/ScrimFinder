package fc.ul.scrimfinder.system;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("system")
class PipelineSystemIT {

    private static final String BASE_URL =
            normalizeBaseUrl(
                    firstNonBlank(System.getenv("BASE_URL"), System.getenv("SCRIM_SYSTEM_BASE_URL")));
    private static final String RIOT_MATCH_ID = "EUW1_7824036381";
    private static final List<RiotAccount> RIOT_ACCOUNTS =
            List.of(
                    new RiotAccount("Simoes88", "nr1"),
                    new RiotAccount("Dalavar", "JGL"),
                    new RiotAccount("Aldric", "8888"),
                    new RiotAccount("Prov1dencXile", "EUW"),
                    new RiotAccount("Sandocha", "asmei"),
                    new RiotAccount("NoTanksAcc", "ORNN"),
                    new RiotAccount("sousa", "balls"),
                    new RiotAccount("Syklash", "Fail"),
                    new RiotAccount("MetroArcher", "EUW"),
                    new RiotAccount("Kitsune", "bruby"));

    private static final HttpClient CLIENT =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void deployedCoreServicesAreReady() throws Exception {
        requireSystemBaseUrl();

        assertSuccess(get("/api/v1/matchmaking/q/health/ready"), "matchmaking readiness");
        assertSuccess(get("/api/v1/ranking/q/health/ready"), "ranking readiness");
        assertSuccess(get("/api/v1/history/q/health/ready"), "history readiness");
    }

    @Test
    void matchmakingRankingHistoryPipelineCompletesOnDeployedEnvironment() throws Exception {
        requireSystemBaseUrl();

        String runId = UUID.randomUUID().toString().substring(0, 8);
        String queueId = UUID.randomUUID().toString();

        assertSuccess(
                post(
                        "/api/v1/matchmaking/queues",
                        json(
                                object(
                                        "id",
                                        queueId,
                                        "name",
                                        "Final Test " + runId,
                                        "namespace",
                                        null,
                                        "requiredPlayers",
                                        RIOT_ACCOUNTS.size(),
                                        "isRoleQueue",
                                        false,
                                        "mode",
                                        "NORMAL",
                                        "mmrWindow",
                                        200,
                                        "region",
                                        "EUW"))),
                "queue creation");

        List<PlayerFixture> players = createAndLinkPlayers(runId);
        sleep(Duration.ofSeconds(2));

        List<JsonNode> tickets = new ArrayList<>();
        for (PlayerFixture player : players) {
            HttpResponse<String> response =
                    post(
                            "/api/v1/matchmaking/tickets",
                            json(Map.of("playerId", player.id(), "queueId", queueId)));
            assertSuccess(response, "queue join for " + player.username());
            tickets.add(parse(response));
        }

        JsonNode lobby = waitForLobby(tickets.get(0).path("id").asText());
        String lobbyId = requiredText(lobby, "id");
        String matchId = requiredText(lobby, "matchId");
        assertFalse(lobbyId.isBlank(), "lobby id should be present");
        assertFalse(matchId.isBlank(), "match id should be present");

        for (PlayerFixture player : players) {
            assertSuccess(
                    post(
                            "/api/v1/matchmaking/tickets/matches/"
                                    + encodePathSegment(matchId)
                                    + "/players/"
                                    + encodePathSegment(player.id())
                                    + "/accept"),
                    "match acceptance for " + player.username());
        }

        assertSuccess(
                put(
                        "/api/v1/matchmaking/tickets/matches/" + encodePathSegment(matchId) + "/link",
                        json(Map.of("externalGameId", RIOT_MATCH_ID))),
                "match link to valid Riot match id");

        sleep(Duration.ofSeconds(2));

        assertSuccess(
                post("/api/v1/matchmaking/tickets/matches/" + encodePathSegment(matchId) + "/complete"),
                "match completion");

        sleep(Duration.ofSeconds(15));

        assertSuccess(
                get("/api/v1/history/matches/" + encodePathSegment(RIOT_MATCH_ID)),
                "history lookup for completed Riot match");

        HttpResponse<String> rankingResponse =
                get(
                        "/api/v1/ranking/players/"
                                + encodePathSegment(players.get(0).id())
                                + "/queue-rankings/"
                                + encodePathSegment(queueId));
        assertSuccess(rankingResponse, "ranking lookup after completed match");

        JsonNode rankings = parse(rankingResponse);
        assertTrue(rankings.isArray(), "queue ranking response should be an array");
        assertFalse(rankings.isEmpty(), "queue ranking response should contain a ranking");
        assertTrue(
                rankings.get(0).path("mmr").asInt(1000) != 1000,
                "ranking MMR should change after processing the fixed Riot match");
    }

    private static List<PlayerFixture> createAndLinkPlayers(String runId) throws Exception {
        List<PlayerFixture> players = new ArrayList<>();

        for (RiotAccount account : RIOT_ACCOUNTS) {
            String playerId =
                    UUID.nameUUIDFromBytes(
                                    (account.gameName().toLowerCase().replace(" ", "") + runId)
                                            .getBytes(StandardCharsets.UTF_8))
                            .toString();
            String username = account.gameName().replace(" ", "_").toLowerCase() + "_" + runId;

            delete(
                    "/api/v1/ranking/players/links/"
                            + encodePathSegment(account.gameName())
                            + "/"
                            + encodePathSegment(account.tagLine()));

            post(
                    "/api/v1/matchmaking/players", json(Map.of("id", playerId, "discordUsername", username)));

            assertSuccess(
                    put(
                            "/api/v1/ranking/players/" + encodePathSegment(playerId) + "/link-lol-account",
                            json(
                                    object(
                                            "puuid",
                                            null,
                                            "gameName",
                                            account.gameName(),
                                            "tagLine",
                                            account.tagLine(),
                                            "region",
                                            "EUW"))),
                    "link Riot account " + account.gameName() + "#" + account.tagLine());

            assertSuccess(
                    post("/api/v1/ranking/players/" + encodePathSegment(playerId) + "/sync-mmr"),
                    "sync MMR for " + account.gameName() + "#" + account.tagLine());

            players.add(new PlayerFixture(playerId, username));
        }

        return players;
    }

    private static JsonNode waitForLobby(String ticketId) throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            sleep(Duration.ofSeconds(1));
            HttpResponse<String> response =
                    get("/api/v1/matchmaking/tickets/" + encodePathSegment(ticketId) + "/lobby");
            if (response.statusCode() == 200) {
                return parse(response);
            }
        }

        throw new AssertionError("Matchmaking timed out waiting for lobby for ticket " + ticketId);
    }

    private static String requiredText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        assertNotNull(value, "response should contain field " + fieldName);
        return value.asText();
    }

    private static void requireSystemBaseUrl() {
        Assumptions.assumeTrue(
                Boolean.getBoolean("systemTests"), "systemTests property is required for deployed tests");
        Assumptions.assumeTrue(
                BASE_URL != null && !BASE_URL.isBlank(),
                "BASE_URL is required for deployed GKE system tests");
    }

    private static HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .timeout(Duration.ofSeconds(30))
                        .version(HttpClient.Version.HTTP_1_1)
                        .GET()
                        .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String path) throws IOException, InterruptedException {
        return post(path, "");
    }

    private static HttpResponse<String> post(String path, String json)
            throws IOException, InterruptedException {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .timeout(Duration.ofSeconds(30))
                        .version(HttpClient.Version.HTTP_1_1)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> put(String path, String json)
            throws IOException, InterruptedException {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .timeout(Duration.ofSeconds(30))
                        .version(HttpClient.Version.HTTP_1_1)
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(json))
                        .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void delete(String path) throws IOException, InterruptedException {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .timeout(Duration.ofSeconds(30))
                        .version(HttpClient.Version.HTTP_1_1)
                        .DELETE()
                        .build();
        CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode parse(HttpResponse<String> response) throws IOException {
        return OBJECT_MAPPER.readTree(response.body());
    }

    private static String json(Map<String, Object> values) throws IOException {
        return OBJECT_MAPPER.writeValueAsString(values);
    }

    private static Map<String, Object> object(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            values.put((String) pairs[i], pairs[i + 1]);
        }
        return values;
    }

    private static void assertSuccess(HttpResponse<String> response, String operation) {
        int status = response.statusCode();
        assertTrue(
                status >= 200 && status < 300,
                () -> operation + " failed with HTTP " + status + ": " + response.body());
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static void sleep(Duration duration) throws InterruptedException {
        Thread.sleep(duration.toMillis());
    }

    private record RiotAccount(String gameName, String tagLine) {}

    private record PlayerFixture(String id, String username) {}
}
