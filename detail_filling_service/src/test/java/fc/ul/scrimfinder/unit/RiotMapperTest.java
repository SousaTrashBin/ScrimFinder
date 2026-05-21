package fc.ul.scrimfinder.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fc.ul.scrimfinder.dto.response.match.MatchStatsDTO;
import fc.ul.scrimfinder.dto.response.match.PlayerStatsDTO;
import fc.ul.scrimfinder.dto.response.match.TeamStatsDTO;
import fc.ul.scrimfinder.dto.response.player.*;
import fc.ul.scrimfinder.exception.InvalidMatchFormatException;
import fc.ul.scrimfinder.exception.InvalidPlayerFormatException;
import fc.ul.scrimfinder.exception.InvalidTeamFormatException;
import fc.ul.scrimfinder.mapper.RiotMapper;
import fc.ul.scrimfinder.util.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.json.Json;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;

@QuarkusTest
@Tag("unit")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RiotMapperTest {

    @Inject RiotMapper riotMapper;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String matchJsonString;
    private static JsonNode match;

    private static String accountJsonString;
    private static JsonNode account;
    private static String regionJsonString;
    private static JsonNode region;
    private static String summonerJsonString;
    private static JsonNode summoner;
    private static String playerQueueStatsJsonString;
    private static JsonNode playerQueueStats;

    private static List<PlayerStatsDTO> playerStatsDTOList;
    private static List<TeamStatsDTO> teamStatsDTOList;
    private static MatchStatsDTO matchStatsDTO;

    private static AccountDTO accountDTO;
    private static RegionDTO regionDTO;
    private static SummonerDTO summonerDTO;
    private static Rank rank;
    private static PlayerQueueStatsDTO playerQueueStatsDTO;

    @BeforeAll
    public static void init() {
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        String matchId = "EUW1_" + UUID.randomUUID();

        matchJsonString =
                Json.createObjectBuilder()
                        .add("metadata", Json.createObjectBuilder().add("matchId", matchId).build())
                        .add(
                                "info",
                                Json.createObjectBuilder()
                                        .add("gameVersion", "15.5.8421098.0")
                                        .add("gameStartTimestamp", 100000L)
                                        .add("gameDuration", 1000L)
                                        .add(
                                                "participants",
                                                Json.createArrayBuilder()
                                                        .add(
                                                                Json.createObjectBuilder()
                                                                        .add("puuid", "puuid1")
                                                                        .add("riotIdGameName", "kung")
                                                                        .add("riotIdTagline", "foo")
                                                                        .add("profileIcon", 1L)
                                                                        .add("summonerLevel", 200)
                                                                        .add("kills", 27)
                                                                        .add("deaths", 30)
                                                                        .add("assists", 5)
                                                                        .add("totalHeal", 1000)
                                                                        .add("totalDamageDealt", 4000)
                                                                        .add("wardsPlaced", 7)
                                                                        .add("goldEarned", 1234)
                                                                        .add("teamPosition", "TOP")
                                                                        .add("championName", "Morgana")
                                                                        .add("totalMinionsKilled", 4)
                                                                        .add("tripleKills", 3)
                                                                        .add("quadraKills", 2)
                                                                        .add("pentaKills", 1)
                                                                        .add("teamId", 100)
                                                                        .add("win", true)
                                                                        .build())
                                                        .add(
                                                                Json.createObjectBuilder()
                                                                        .add("puuid", "puuid2")
                                                                        .add("riotIdGameName", "TaiLung")
                                                                        .add("riotIdTagline", "nofoo")
                                                                        .add("profileIcon", 2L)
                                                                        .add("summonerLevel", 100)
                                                                        .add("kills", 5)
                                                                        .add("deaths", 30)
                                                                        .add("assists", 5)
                                                                        .add("totalHeal", 0)
                                                                        .add("totalDamageDealt", 6000)
                                                                        .add("wardsPlaced", 2)
                                                                        .add("goldEarned", 1000)
                                                                        .add("teamPosition", "JUNGLE")
                                                                        .add("championName", "Alistar")
                                                                        .add("totalMinionsKilled", 6)
                                                                        .add("tripleKills", 2)
                                                                        .add("quadraKills", 1)
                                                                        .add("pentaKills", 0)
                                                                        .add("teamId", 200)
                                                                        .add("win", false)
                                                                        .build())
                                                        .build())
                                        .add(
                                                "teams",
                                                Json.createArrayBuilder()
                                                        .add(Json.createObjectBuilder().add("teamId", 100).build())
                                                        .add(Json.createObjectBuilder().add("teamId", 200).build()))
                                        .build())
                        .build()
                        .toString();

        accountJsonString =
                Json.createObjectBuilder()
                        .add("puuid", "puuid1")
                        .add("gameName", "kung")
                        .add("tagLine", "foo")
                        .build()
                        .toString();

        regionJsonString = Json.createObjectBuilder().add("region", "euw1").build().toString();

        summonerJsonString =
                Json.createObjectBuilder()
                        .add("profileIconId", 1)
                        .add("summonerLevel", 200L)
                        .build()
                        .toString();

        playerQueueStatsJsonString =
                Json.createObjectBuilder()
                        .add("queueType", "RANKED_QUEUE")
                        .add("tier", "DIAMOND")
                        .add("rank", "II")
                        .add("leaguePoints", 50)
                        .add("wins", 100)
                        .add("losses", 95)
                        .add("hotStreak", false)
                        .build()
                        .toString();

        playerStatsDTOList =
                List.of(
                        new PlayerStatsDTO(
                                new RiotId("puuid1", "kung", "foo", 1, 200),
                                27,
                                30,
                                5,
                                1000,
                                4000,
                                7,
                                1234,
                                Role.TOP,
                                "Morgana",
                                4,
                                3,
                                2,
                                1,
                                TeamSide.BLUE,
                                true),
                        new PlayerStatsDTO(
                                new RiotId("puuid2", "TaiLung", "nofoo", 2, 100),
                                5,
                                30,
                                5,
                                0,
                                6000,
                                2,
                                1000,
                                Role.JUNGLE,
                                "Alistar",
                                6,
                                2,
                                1,
                                0,
                                TeamSide.RED,
                                false));

        teamStatsDTOList =
                List.of(
                        new TeamStatsDTO(TeamSide.BLUE, 27, 30, 5, 1000),
                        new TeamStatsDTO(TeamSide.RED, 5, 30, 5, 0));

        matchStatsDTO =
                new MatchStatsDTO(matchId, "15.5", 100000L, 1000L, playerStatsDTOList, teamStatsDTOList);

        accountDTO = new AccountDTO("puuid1", "kung", "foo");

        regionDTO = new RegionDTO("europe", "euw1");

        summonerDTO = new SummonerDTO(1, 200L);

        rank = new Rank(Tier.DIAMOND, 2, 50);

        playerQueueStatsDTO = new PlayerQueueStatsDTO("RANKED_QUEUE", rank, 100, 95, false);
    }

    @BeforeEach
    public void setup() throws JsonProcessingException {
        match = MAPPER.readTree(matchJsonString);
        account = MAPPER.readTree(accountJsonString);
        region = MAPPER.readTree(regionJsonString);
        summoner = MAPPER.readTree(summonerJsonString);
        playerQueueStats = MAPPER.readTree(playerQueueStatsJsonString);
    }

    @Test
    @Order(1)
    public void testToMatchStatsDTO_ShouldThrowInvalidMatchFormatException_WhenNoMetadata() {
        ObjectNode matchObject = (ObjectNode) match;
        matchObject.remove("metadata");
        match = MAPPER.convertValue(matchObject, JsonNode.class);
        assertThrows(InvalidMatchFormatException.class, () -> riotMapper.toMatchStatsDTO(match));
    }

    @Test
    @Order(2)
    public void testToMatchStatsDTO_ShouldThrowInvalidMatchFormatException_WhenNoMatchId() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode metadataObject = (ObjectNode) matchObject.path("metadata");
        metadataObject.remove("matchId");
        match = MAPPER.convertValue(matchObject, JsonNode.class);
        assertThrows(InvalidMatchFormatException.class, () -> riotMapper.toMatchStatsDTO(match));
    }

    @Test
    @Order(3)
    public void testToMatchStatsDTO_ShouldThrowInvalidMatchFormatException_WhenNoInfo() {
        ObjectNode matchObject = (ObjectNode) match;
        matchObject.remove("info");
        match = MAPPER.convertValue(matchObject, JsonNode.class);
        assertThrows(InvalidMatchFormatException.class, () -> riotMapper.toMatchStatsDTO(match));
    }

    @Test
    @Order(4)
    public void testToMatchStatsDTO_ShouldThrowInvalidMatchFormatException_WhenNoGameVersion() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode infoObject = (ObjectNode) matchObject.path("info");
        infoObject.remove("gameVersion");
        match = MAPPER.convertValue(matchObject, JsonNode.class);
        assertThrows(InvalidMatchFormatException.class, () -> riotMapper.toMatchStatsDTO(match));
    }

    @Test
    @Order(5)
    public void
            testToMatchStatsDTO_ShouldThrowInvalidMatchFormatException_WhenGameVersionHasLessThanTwoParts()
                    throws JsonProcessingException {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode infoObject = (ObjectNode) matchObject.path("info");
        infoObject.replace("gameVersion", MAPPER.readTree("15"));
        match = MAPPER.convertValue(matchObject, JsonNode.class);
        assertThrows(InvalidMatchFormatException.class, () -> riotMapper.toMatchStatsDTO(match));
    }

    @Test
    @Order(6)
    public void
            testToMatchStatsDTO_ShouldThrowInvalidMatchFormatException_WhenNoGameStartTimestamp() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode infoObject = (ObjectNode) matchObject.path("info");
        infoObject.remove("gameStartTimestamp");
        match = MAPPER.convertValue(matchObject, JsonNode.class);
        assertThrows(InvalidMatchFormatException.class, () -> riotMapper.toMatchStatsDTO(match));
    }

    @Test
    @Order(7)
    public void testToMatchStatsDTO_ShouldThrowInvalidMatchFormatException_WhenNoGameDuration() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode infoObject = (ObjectNode) matchObject.path("info");
        infoObject.remove("gameDuration");
        match = MAPPER.convertValue(matchObject, JsonNode.class);
        assertThrows(InvalidMatchFormatException.class, () -> riotMapper.toMatchStatsDTO(match));
    }

    @Test
    @Order(8)
    public void testToMatchStatsDTO_ShouldThrowInvalidMatchFormatException_WhenNoParticipants() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode infoObject = (ObjectNode) matchObject.path("info");
        infoObject.remove("participants");
        match = MAPPER.convertValue(matchObject, JsonNode.class);
        assertThrows(InvalidMatchFormatException.class, () -> riotMapper.toMatchStatsDTO(match));
    }

    @Test
    @Order(9)
    public void testToMatchStatsDTO_ShouldThrowInvalidMatchFormatException_WhenNoTeams() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode infoObject = (ObjectNode) matchObject.path("info");
        infoObject.remove("teams");
        match = MAPPER.convertValue(matchObject, JsonNode.class);
        assertThrows(InvalidMatchFormatException.class, () -> riotMapper.toMatchStatsDTO(match));
    }

    @Test
    @Order(10)
    public void testToMatchStatsDTO_ShouldReturnMatchStatsDTO_WhenRiotMatchHasAllNeededFields() {
        assertEquals(matchStatsDTO, riotMapper.toMatchStatsDTO(match));
    }

    @Test
    @Order(11)
    public void testToPlayerStatsDTO_ShouldThrowInvalidPlayerFormatException_WhenNoPuuid() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode participantObject =
                (ObjectNode) matchObject.path("info").path("participants").get(0);
        participantObject.remove("puuid");
        JsonNode participant = MAPPER.convertValue(participantObject, JsonNode.class);
        assertThrows(
                InvalidPlayerFormatException.class, () -> riotMapper.toPlayerStatsDTO(participant));
    }

    @Test
    @Order(12)
    public void testToPlayerStatsDTO_ShouldThrowInvalidPlayerFormatException_WhenNoRiotIdGameName() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode participantObject =
                (ObjectNode) matchObject.path("info").path("participants").get(0);
        participantObject.remove("riotIdGameName");
        JsonNode participant = MAPPER.convertValue(participantObject, JsonNode.class);
        assertThrows(
                InvalidPlayerFormatException.class, () -> riotMapper.toPlayerStatsDTO(participant));
    }

    @Test
    @Order(13)
    public void testToPlayerStatsDTO_ShouldThrowInvalidPlayerFormatException_WhenNoRiotIdTagline() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode participantObject =
                (ObjectNode) matchObject.path("info").path("participants").get(0);
        participantObject.remove("riotIdTagline");
        JsonNode participant = MAPPER.convertValue(participantObject, JsonNode.class);
        assertThrows(
                InvalidPlayerFormatException.class, () -> riotMapper.toPlayerStatsDTO(participant));
    }

    @Test
    @Order(14)
    public void testToPlayerStatsDTO_ShouldThrowInvalidPlayerFormatException_WhenNoProfileIcon() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode participantObject =
                (ObjectNode) matchObject.path("info").path("participants").get(0);
        participantObject.remove("profileIcon");
        JsonNode participant = MAPPER.convertValue(participantObject, JsonNode.class);
        assertThrows(
                InvalidPlayerFormatException.class, () -> riotMapper.toPlayerStatsDTO(participant));
    }

    @Test
    @Order(15)
    public void testToPlayerStatsDTO_ShouldThrowInvalidPlayerFormatException_WhenNoSummonerLevel() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode participantObject =
                (ObjectNode) matchObject.path("info").path("participants").get(0);
        participantObject.remove("summonerLevel");
        JsonNode participant = MAPPER.convertValue(participantObject, JsonNode.class);
        assertThrows(
                InvalidPlayerFormatException.class, () -> riotMapper.toPlayerStatsDTO(participant));
    }

    @Test
    @Order(16)
    public void testToPlayerStatsDTO_ShouldThrowInvalidPlayerFormatException_WhenNoKills() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode participantObject =
                (ObjectNode) matchObject.path("info").path("participants").get(0);
        participantObject.remove("kills");
        JsonNode participant = MAPPER.convertValue(participantObject, JsonNode.class);
        assertThrows(
                InvalidPlayerFormatException.class, () -> riotMapper.toPlayerStatsDTO(participant));
    }

    @Test
    @Order(17)
    public void testToPlayerStatsDTO_ShouldThrowInvalidPlayerFormatException_WhenNoDeaths() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode participantObject =
                (ObjectNode) matchObject.path("info").path("participants").get(0);
        participantObject.remove("deaths");
        JsonNode participant = MAPPER.convertValue(participantObject, JsonNode.class);
        assertThrows(
                InvalidPlayerFormatException.class, () -> riotMapper.toPlayerStatsDTO(participant));
    }

    @Test
    @Order(18)
    public void testToPlayerStatsDTO_ShouldThrowInvalidPlayerFormatException_WhenNoAssists() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode participantObject =
                (ObjectNode) matchObject.path("info").path("participants").get(0);
        participantObject.remove("assists");
        JsonNode participant = MAPPER.convertValue(participantObject, JsonNode.class);
        assertThrows(
                InvalidPlayerFormatException.class, () -> riotMapper.toPlayerStatsDTO(participant));
    }

    @Test
    @Order(19)
    public void testToPlayerStatsDTO_ShouldThrowInvalidPlayerFormatException_WhenNoTotalHeal() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode participantObject =
                (ObjectNode) matchObject.path("info").path("participants").get(0);
        participantObject.remove("totalHeal");
        JsonNode participant = MAPPER.convertValue(participantObject, JsonNode.class);
        assertThrows(
                InvalidPlayerFormatException.class, () -> riotMapper.toPlayerStatsDTO(participant));
    }

    @Test
    @Order(20)
    public void
            testToPlayerStatsDTO_ShouldThrowInvalidPlayerFormatException_WhenNoTotalDamageDealt() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode participantObject =
                (ObjectNode) matchObject.path("info").path("participants").get(0);
        participantObject.remove("totalDamageDealt");
        JsonNode participant = MAPPER.convertValue(participantObject, JsonNode.class);
        assertThrows(
                InvalidPlayerFormatException.class, () -> riotMapper.toPlayerStatsDTO(participant));
    }

    @Test
    @Order(21)
    public void testToPlayerStatsDTO_ShouldThrowInvalidPlayerFormatException_WhenNoWardsPlaced() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode participantObject =
                (ObjectNode) matchObject.path("info").path("participants").get(0);
        participantObject.remove("wardsPlaced");
        JsonNode participant = MAPPER.convertValue(participantObject, JsonNode.class);
        assertThrows(
                InvalidPlayerFormatException.class, () -> riotMapper.toPlayerStatsDTO(participant));
    }

    @Test
    @Order(22)
    public void testToPlayerStatsDTO_ShouldThrowInvalidPlayerFormatException_WhenNoGoldEarned() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode participantObject =
                (ObjectNode) matchObject.path("info").path("participants").get(0);
        participantObject.remove("goldEarned");
        JsonNode participant = MAPPER.convertValue(participantObject, JsonNode.class);
        assertThrows(
                InvalidPlayerFormatException.class, () -> riotMapper.toPlayerStatsDTO(participant));
    }

    @Test
    @Order(23)
    public void testToPlayerStatsDTO_ShouldThrowInvalidPlayerFormatException_WhenNoTeamPosition() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode participantObject =
                (ObjectNode) matchObject.path("info").path("participants").get(0);
        participantObject.remove("teamPosition");
        JsonNode participant = MAPPER.convertValue(participantObject, JsonNode.class);
        assertThrows(
                InvalidPlayerFormatException.class, () -> riotMapper.toPlayerStatsDTO(participant));
    }

    @Test
    @Order(24)
    public void testToPlayerStatsDTO_ShouldThrowInvalidPlayerFormatException_WhenNoChampionName() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode participantObject =
                (ObjectNode) matchObject.path("info").path("participants").get(0);
        participantObject.remove("championName");
        JsonNode participant = MAPPER.convertValue(participantObject, JsonNode.class);
        assertThrows(
                InvalidPlayerFormatException.class, () -> riotMapper.toPlayerStatsDTO(participant));
    }

    @Test
    @Order(25)
    public void
            testToPlayerStatsDTO_ShouldThrowInvalidPlayerFormatException_WhenNoTotalMinionsKilled() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode participantObject =
                (ObjectNode) matchObject.path("info").path("participants").get(0);
        participantObject.remove("totalMinionsKilled");
        JsonNode participant = MAPPER.convertValue(participantObject, JsonNode.class);
        assertThrows(
                InvalidPlayerFormatException.class, () -> riotMapper.toPlayerStatsDTO(participant));
    }

    @Test
    @Order(26)
    public void testToPlayerStatsDTO_ShouldThrowInvalidPlayerFormatException_WhenNoTripleKills() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode participantObject =
                (ObjectNode) matchObject.path("info").path("participants").get(0);
        participantObject.remove("tripleKills");
        JsonNode participant = MAPPER.convertValue(participantObject, JsonNode.class);
        assertThrows(
                InvalidPlayerFormatException.class, () -> riotMapper.toPlayerStatsDTO(participant));
    }

    @Test
    @Order(27)
    public void testToPlayerStatsDTO_ShouldThrowInvalidPlayerFormatException_WhenNoQuadraKills() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode participantObject =
                (ObjectNode) matchObject.path("info").path("participants").get(0);
        participantObject.remove("quadraKills");
        JsonNode participant = MAPPER.convertValue(participantObject, JsonNode.class);
        assertThrows(
                InvalidPlayerFormatException.class, () -> riotMapper.toPlayerStatsDTO(participant));
    }

    @Test
    @Order(28)
    public void testToPlayerStatsDTO_ShouldThrowInvalidPlayerFormatException_WhenNoPentaKills() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode participantObject =
                (ObjectNode) matchObject.path("info").path("participants").get(0);
        participantObject.remove("pentaKills");
        JsonNode participant = MAPPER.convertValue(participantObject, JsonNode.class);
        assertThrows(
                InvalidPlayerFormatException.class, () -> riotMapper.toPlayerStatsDTO(participant));
    }

    @Test
    @Order(29)
    public void testToPlayerStatsDTO_ShouldThrowInvalidPlayerFormatException_WhenNoTeamId() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode participantObject =
                (ObjectNode) matchObject.path("info").path("participants").get(0);
        participantObject.remove("teamId");
        JsonNode participant = MAPPER.convertValue(participantObject, JsonNode.class);
        assertThrows(
                InvalidPlayerFormatException.class, () -> riotMapper.toPlayerStatsDTO(participant));
    }

    @Test
    @Order(30)
    public void testToPlayerStatsDTO_ShouldThrowInvalidPlayerFormatException_WhenInvalidTeamId()
            throws JsonProcessingException {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode participantObject =
                (ObjectNode) matchObject.path("info").path("participants").get(0);
        participantObject.replace("teamId", MAPPER.readTree("300"));
        JsonNode participant = MAPPER.convertValue(participantObject, JsonNode.class);
        assertThrows(
                InvalidPlayerFormatException.class, () -> riotMapper.toPlayerStatsDTO(participant));
    }

    @Test
    @Order(31)
    public void testToPlayerStatsDTO_ShouldReturnTeamBlue_WhenTeamIdIs100()
            throws JsonProcessingException {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode participantObject =
                (ObjectNode) matchObject.path("info").path("participants").get(0);
        participantObject.replace("teamId", MAPPER.readTree("100"));
        JsonNode participant = MAPPER.convertValue(participantObject, JsonNode.class);
        assertEquals(TeamSide.BLUE, riotMapper.toPlayerStatsDTO(participant).side());
    }

    @Test
    @Order(32)
    public void testToPlayerStatsDTO_ShouldReturnTeamRed_WhenTeamIdIs200()
            throws JsonProcessingException {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode participantObject =
                (ObjectNode) matchObject.path("info").path("participants").get(0);
        participantObject.replace("teamId", MAPPER.readTree("200"));
        JsonNode participant = MAPPER.convertValue(participantObject, JsonNode.class);
        assertEquals(TeamSide.RED, riotMapper.toPlayerStatsDTO(participant).side());
    }

    @Test
    @Order(33)
    public void testToPlayerStatsDTO_ShouldThrowInvalidPlayerFormatException_WhenNoWin() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode participantObject =
                (ObjectNode) matchObject.path("info").path("participants").get(0);
        participantObject.remove("win");
        JsonNode participant = MAPPER.convertValue(participantObject, JsonNode.class);
        assertThrows(
                InvalidPlayerFormatException.class, () -> riotMapper.toPlayerStatsDTO(participant));
    }

    @Test
    @Order(34)
    public void
            testToPlayerStatsDTO_ShouldReturnPlayerStatsDTO_WhenRiotParticipantHasAllNeededFields() {
        JsonNode participant = match.get("info").get("participants").get(0);
        assertEquals(playerStatsDTOList.getFirst(), riotMapper.toPlayerStatsDTO(participant));
    }

    @Test
    @Order(35)
    public void testToTeamStatsDTO_ShouldThrowInvalidTeamFormatException_WhenNoTeamId() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode teamObject = (ObjectNode) matchObject.path("info").path("teams").get(0);
        teamObject.remove("teamId");
        JsonNode team = MAPPER.convertValue(teamObject, JsonNode.class);
        assertThrows(
                InvalidTeamFormatException.class,
                () -> riotMapper.toTeamStatsDTO(team, playerStatsDTOList));
    }

    @Test
    @Order(36)
    public void testToTeamStatsDTO_ShouldThrowInvalidTeamFormatException_WhenInvalidTeamId()
            throws JsonProcessingException {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode teamObject = (ObjectNode) matchObject.path("info").path("teams").get(0);
        teamObject.replace("teamId", MAPPER.readTree("300"));
        JsonNode team = MAPPER.convertValue(teamObject, JsonNode.class);
        assertThrows(
                InvalidTeamFormatException.class,
                () -> riotMapper.toTeamStatsDTO(team, playerStatsDTOList));
    }

    @Test
    @Order(37)
    public void testToTeamStatsDTO_ShouldReturnTeamBlue_WhenTeamIdIs100()
            throws JsonProcessingException {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode teamObject = (ObjectNode) matchObject.path("info").path("teams").get(0);
        teamObject.replace("teamId", MAPPER.readTree("100"));
        JsonNode team = MAPPER.convertValue(teamObject, JsonNode.class);
        assertEquals(TeamSide.BLUE, riotMapper.toTeamStatsDTO(team, playerStatsDTOList).side());
    }

    @Test
    @Order(38)
    public void testToTeamStatsDTO_ShouldReturnTeamRed_WhenTeamIdIs200()
            throws JsonProcessingException {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode teamObject = (ObjectNode) matchObject.path("info").path("teams").get(0);
        teamObject.replace("teamId", MAPPER.readTree("200"));
        JsonNode team = MAPPER.convertValue(teamObject, JsonNode.class);
        assertEquals(TeamSide.RED, riotMapper.toTeamStatsDTO(team, playerStatsDTOList).side());
    }

    @Test
    @Order(39)
    public void testToTeamStatsDTO_ShouldReturnTeamStatsDTO_WhenRiotTeamHasAllNeededFields() {
        JsonNode team = match.get("info").get("teams").get(0);
        assertEquals(
                teamStatsDTOList.getFirst(),
                riotMapper.toTeamStatsDTO(team, List.of(playerStatsDTOList.getFirst())));
    }

    @Test
    @Order(40)
    public void testToAccountDTO_ShouldThrowInvalidPlayerFormatException_WhenNoPuuid() {
        ObjectNode accountObject = (ObjectNode) account;
        accountObject.remove("puuid");
        account = MAPPER.convertValue(accountObject, JsonNode.class);
        assertThrows(InvalidPlayerFormatException.class, () -> riotMapper.toAccountDTO(account));
    }

    @Test
    @Order(41)
    public void testToAccountDTO_ShouldThrowInvalidPlayerFormatException_WhenNoGameName() {
        ObjectNode accountObject = (ObjectNode) account;
        accountObject.remove("gameName");
        account = MAPPER.convertValue(accountObject, JsonNode.class);
        assertThrows(InvalidPlayerFormatException.class, () -> riotMapper.toAccountDTO(account));
    }

    @Test
    @Order(42)
    public void testToAccountDTO_ShouldThrowInvalidPlayerFormatException_WhenNoTagLine() {
        ObjectNode accountObject = (ObjectNode) account;
        accountObject.remove("tagLine");
        account = MAPPER.convertValue(accountObject, JsonNode.class);
        assertThrows(InvalidPlayerFormatException.class, () -> riotMapper.toAccountDTO(account));
    }

    @Test
    @Order(43)
    public void testToAccountDTO_ShouldReturnAccountDTO_WhenRiotAccountHasAllNeededFields() {
        assertEquals(accountDTO, riotMapper.toAccountDTO(account));
    }

    @Test
    @Order(44)
    public void testToRegionDTO_ShouldThrowInvalidPlayerFormatException_WhenNoRegion() {
        ObjectNode regionObject = (ObjectNode) region;
        regionObject.remove("region");
        region = MAPPER.convertValue(regionObject, JsonNode.class);
        assertThrows(InvalidPlayerFormatException.class, () -> riotMapper.toRegionDTO(region));
    }

    @Test
    @Order(45)
    public void testToRegionDTO_ShouldReturnRegionDTO_WhenRiotRegionHasAllNeededFields() {
        assertEquals(regionDTO, riotMapper.toRegionDTO(region));
    }

    @Test
    @Order(46)
    public void testToSummonerDTO_ShouldThrowInvalidPlayerFormatException_WhenNoProfileIconId() {
        ObjectNode summonerObject = (ObjectNode) summoner;
        summonerObject.remove("profileIconId");
        summoner = MAPPER.convertValue(summonerObject, JsonNode.class);
        assertThrows(InvalidPlayerFormatException.class, () -> riotMapper.toSummonerDTO(summoner));
    }

    @Test
    @Order(47)
    public void testToSummonerDTO_ShouldThrowInvalidPlayerFormatException_WhenNoSummonerLevel() {
        ObjectNode summonerObject = (ObjectNode) summoner;
        summonerObject.remove("summonerLevel");
        summoner = MAPPER.convertValue(summonerObject, JsonNode.class);
        assertThrows(InvalidPlayerFormatException.class, () -> riotMapper.toSummonerDTO(summoner));
    }

    @Test
    @Order(48)
    public void testToSummonerDTO_ShouldReturnSummonerDTO_WhenRiotSummonerHasAllNeededFields() {
        assertEquals(summonerDTO, riotMapper.toSummonerDTO(summoner));
    }

    @Test
    @Order(49)
    public void testToPlayerQueueStatsDTO_ShouldThrowInvalidPlayerFormatException_WhenNoQueueType() {
        ObjectNode playerQueueStatsObject = (ObjectNode) playerQueueStats;
        playerQueueStatsObject.remove("queueType");
        playerQueueStats = MAPPER.convertValue(playerQueueStatsObject, JsonNode.class);
        assertThrows(
                InvalidPlayerFormatException.class,
                () -> riotMapper.toPlayerQueueStatsDTO(playerQueueStats));
    }

    @Test
    @Order(50)
    public void testToRank_ShouldThrowInvalidPlayerFormatException_WhenNoTier() {
        ObjectNode playerQueueStatsObject = (ObjectNode) playerQueueStats;
        playerQueueStatsObject.remove("tier");
        playerQueueStats = MAPPER.convertValue(playerQueueStatsObject, JsonNode.class);
        assertThrows(InvalidPlayerFormatException.class, () -> riotMapper.toRank(playerQueueStats));
    }

    @Test
    @Order(51)
    public void testToRank_ShouldThrowInvalidPlayerFormatException_WhenNoRank() {
        ObjectNode playerQueueStatsObject = (ObjectNode) playerQueueStats;
        playerQueueStatsObject.remove("rank");
        playerQueueStats = MAPPER.convertValue(playerQueueStatsObject, JsonNode.class);
        assertThrows(InvalidPlayerFormatException.class, () -> riotMapper.toRank(playerQueueStats));
    }

    @Test
    @Order(52)
    public void testToRank_ShouldThrowInvalidPlayerFormatException_WhenNoLeaguePoints() {
        ObjectNode playerQueueStatsObject = (ObjectNode) playerQueueStats;
        playerQueueStatsObject.remove("leaguePoints");
        playerQueueStats = MAPPER.convertValue(playerQueueStatsObject, JsonNode.class);
        assertThrows(InvalidPlayerFormatException.class, () -> riotMapper.toRank(playerQueueStats));
    }

    @Test
    @Order(53)
    public void testToRank_ShouldReturnRank_WhenRiotPlayerQueueStatsHasAllNeededFields() {
        assertEquals(rank, riotMapper.toRank(playerQueueStats));
    }

    @Test
    @Order(54)
    public void testToPlayerQueueStatsDTO_ShouldThrowInvalidPlayerFormatException_WhenNoWins() {
        ObjectNode playerQueueStatsObject = (ObjectNode) playerQueueStats;
        playerQueueStatsObject.remove("wins");
        playerQueueStats = MAPPER.convertValue(playerQueueStatsObject, JsonNode.class);
        assertThrows(
                InvalidPlayerFormatException.class,
                () -> riotMapper.toPlayerQueueStatsDTO(playerQueueStats));
    }

    @Test
    @Order(55)
    public void testToPlayerQueueStatsDTO_ShouldThrowInvalidPlayerFormatException_WhenNoLosses() {
        ObjectNode playerQueueStatsObject = (ObjectNode) playerQueueStats;
        playerQueueStatsObject.remove("losses");
        playerQueueStats = MAPPER.convertValue(playerQueueStatsObject, JsonNode.class);
        assertThrows(
                InvalidPlayerFormatException.class,
                () -> riotMapper.toPlayerQueueStatsDTO(playerQueueStats));
    }

    @Test
    @Order(56)
    public void testToPlayerQueueStatsDTO_ShouldThrowInvalidPlayerFormatException_WhenNoHotStreak() {
        ObjectNode playerQueueStatsObject = (ObjectNode) playerQueueStats;
        playerQueueStatsObject.remove("hotStreak");
        playerQueueStats = MAPPER.convertValue(playerQueueStatsObject, JsonNode.class);
        assertThrows(
                InvalidPlayerFormatException.class,
                () -> riotMapper.toPlayerQueueStatsDTO(playerQueueStats));
    }

    @Test
    @Order(57)
    public void
            testToPlayerQueueStatsDTO_ShouldReturnPlayerQueueStatsDTO_WhenRiotPlayerQueueStatsHasAllNeededFields() {
        assertEquals(playerQueueStatsDTO, riotMapper.toPlayerQueueStatsDTO(playerQueueStats));
    }
}
