package fc.ul.scrimfinder.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fc.ul.scrimfinder.dto.request.PlayerStatsDTO;
import fc.ul.scrimfinder.dto.request.TeamStatsDTO;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.exception.InvalidExternalJsonFormatException;
import fc.ul.scrimfinder.util.Champion;
import fc.ul.scrimfinder.util.RiotId;
import fc.ul.scrimfinder.util.Role;
import fc.ul.scrimfinder.util.TeamSide;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.json.Json;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;

@QuarkusTest
@Tag("unit")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DetailFillingAdapterServiceTest {

    @Inject DetailFillingAdapterService detailFillingAdapterService;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String matchJsonString;
    private static JsonNode match;

    private static List<PlayerStatsDTO> playerStatsDTOList;
    private static List<TeamStatsDTO> teamStatsDTOList;
    private static MatchDTO matchDTO;

    @BeforeAll
    public static void init() {
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        String matchId = "EUW1_" + UUID.randomUUID();

        matchJsonString =
                Json.createObjectBuilder()
                        .add("riotMatchId", matchId)
                        .add("patch", "15.5.8421098.0")
                        .add("gameCreation", 100000L)
                        .add("gameDuration", 1000L)
                        .add(
                                "players",
                                Json.createArrayBuilder()
                                        .add(
                                                Json.createObjectBuilder()
                                                        .add(
                                                                "riotId",
                                                                Json.createObjectBuilder()
                                                                        .add("puuid", "puuid1")
                                                                        .add("playerName", "kung")
                                                                        .add("playerTag", "foo")
                                                                        .add("summonerIcon", 1L)
                                                                        .add("summonerLevel", 200)
                                                                        .build())
                                                        .add("kills", 27)
                                                        .add("deaths", 30)
                                                        .add("assists", 5)
                                                        .add("healing", 1000)
                                                        .add("damageToPlayers", 4000)
                                                        .add("wards", 7)
                                                        .add("gold", 1234)
                                                        .add("role", "TOP")
                                                        .add("champion", "MORGANA")
                                                        .add("killedMinions", 4)
                                                        .add("tripleKills", 3)
                                                        .add("quadKills", 2)
                                                        .add("pentaKills", 1)
                                                        .add("side", "BLUE")
                                                        .add("won", true)
                                                        .build())
                                        .build())
                        .add(
                                "teams",
                                Json.createArrayBuilder()
                                        .add(
                                                Json.createObjectBuilder()
                                                        .add("side", "BLUE")
                                                        .add("teamKills", 27)
                                                        .add("teamDeaths", 30)
                                                        .add("teamAssists", 5)
                                                        .add("teamHealing", 1000)
                                                        .build())
                                        .build())
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
                                Champion.MORGANA,
                                0.2,
                                4,
                                3,
                                2,
                                1,
                                TeamSide.BLUE,
                                true,
                                null));

        teamStatsDTOList = List.of(new TeamStatsDTO(TeamSide.BLUE, 27, 30, 5, 1000));

        matchDTO =
                new MatchDTO(
                        matchId, null, "15.5.8421098.0", 100000L, 1000L, playerStatsDTOList, teamStatsDTOList);
    }

    @BeforeEach
    public void setup() throws JsonProcessingException {
        match = MAPPER.readTree(matchJsonString);
    }

    @Test
    @Order(1)
    public void
            testMapToMatchFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoRiotMatchId() {
        ObjectNode matchObject = (ObjectNode) match;
        matchObject.remove("riotMatchId");
        match = MAPPER.convertValue(matchObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToMatchFromDetailFilling(match));
    }

    @Test
    @Order(2)
    public void
            testMapToMatchFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoPatch() {
        ObjectNode matchObject = (ObjectNode) match;
        matchObject.remove("patch");
        match = MAPPER.convertValue(matchObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToMatchFromDetailFilling(match));
    }

    @Test
    @Order(3)
    public void
            testMapToMatchFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoGameCreation() {
        ObjectNode matchObject = (ObjectNode) match;
        matchObject.remove("gameCreation");
        match = MAPPER.convertValue(matchObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToMatchFromDetailFilling(match));
    }

    @Test
    @Order(4)
    public void
            testMapToMatchFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoGameDuration() {
        ObjectNode matchObject = (ObjectNode) match;
        matchObject.remove("gameDuration");
        match = MAPPER.convertValue(matchObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToMatchFromDetailFilling(match));
    }

    @Test
    @Order(5)
    public void
            testMapToMatchFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoPlayers() {
        ObjectNode matchObject = (ObjectNode) match;
        matchObject.remove("players");
        match = MAPPER.convertValue(matchObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToMatchFromDetailFilling(match));
    }

    @Test
    @Order(6)
    public void
            testMapToMatchFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoTeams() {
        ObjectNode matchObject = (ObjectNode) match;
        matchObject.remove("teams");
        match = MAPPER.convertValue(matchObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToMatchFromDetailFilling(match));
    }

    @Test
    @Order(7)
    public void
            testMapToMatchFromDetailFilling_ShouldReturnMatchDTO_WhenDetailFillingMatchHasAllNeededFields() {
        assertEquals(matchDTO, detailFillingAdapterService.mapToMatchFromDetailFilling(match));
    }

    @Test
    @Order(8)
    public void
            testMapToPlayerFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoRiotId() {
        Long duration = matchDTO.getGameDuration();
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode playerObject = (ObjectNode) matchObject.path("players").get(0);
        playerObject.remove("riotId");
        JsonNode player = MAPPER.convertValue(playerObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToPlayerFromDetailFilling(player, duration));
    }

    @Test
    @Order(9)
    public void
            testMapToPlayerFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoPuuid() {
        Long duration = matchDTO.getGameDuration();
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode playerObject = (ObjectNode) matchObject.path("players").get(0);
        ObjectNode riotIdObject = (ObjectNode) playerObject.path("riotId");
        riotIdObject.remove("puuid");
        JsonNode player = MAPPER.convertValue(playerObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToPlayerFromDetailFilling(player, duration));
    }

    @Test
    @Order(10)
    public void
            testMapToPlayerFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoPlayerName() {
        Long duration = matchDTO.getGameDuration();
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode playerObject = (ObjectNode) matchObject.path("players").get(0);
        ObjectNode riotIdObject = (ObjectNode) playerObject.path("riotId");
        riotIdObject.remove("playerName");
        JsonNode player = MAPPER.convertValue(playerObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToPlayerFromDetailFilling(player, duration));
    }

    @Test
    @Order(11)
    public void
            testMapToPlayerFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoPlayerTag() {
        Long duration = matchDTO.getGameDuration();
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode playerObject = (ObjectNode) matchObject.path("players").get(0);
        ObjectNode riotIdObject = (ObjectNode) playerObject.path("riotId");
        riotIdObject.remove("playerTag");
        JsonNode player = MAPPER.convertValue(playerObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToPlayerFromDetailFilling(player, duration));
    }

    @Test
    @Order(12)
    public void
            testMapToPlayerFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoSummonerIcon() {
        Long duration = matchDTO.getGameDuration();
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode playerObject = (ObjectNode) matchObject.path("players").get(0);
        ObjectNode riotIdObject = (ObjectNode) playerObject.path("riotId");
        riotIdObject.remove("summonerIcon");
        JsonNode player = MAPPER.convertValue(playerObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToPlayerFromDetailFilling(player, duration));
    }

    @Test
    @Order(13)
    public void
            testMapToPlayerFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoSummonerLevel() {
        Long duration = matchDTO.getGameDuration();
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode playerObject = (ObjectNode) matchObject.path("players").get(0);
        ObjectNode riotIdObject = (ObjectNode) playerObject.path("riotId");
        riotIdObject.remove("summonerLevel");
        JsonNode player = MAPPER.convertValue(playerObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToPlayerFromDetailFilling(player, duration));
    }

    @Test
    @Order(14)
    public void
            testMapToPlayerFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoKills() {
        Long duration = matchDTO.getGameDuration();
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode playerObject = (ObjectNode) matchObject.path("players").get(0);
        playerObject.remove("kills");
        JsonNode player = MAPPER.convertValue(playerObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToPlayerFromDetailFilling(player, duration));
    }

    @Test
    @Order(15)
    public void
            testMapToPlayerFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoDeaths() {
        Long duration = matchDTO.getGameDuration();
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode playerObject = (ObjectNode) matchObject.path("players").get(0);
        playerObject.remove("deaths");
        JsonNode player = MAPPER.convertValue(playerObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToPlayerFromDetailFilling(player, duration));
    }

    @Test
    @Order(16)
    public void
            testMapToPlayerFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoAssists() {
        Long duration = matchDTO.getGameDuration();
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode playerObject = (ObjectNode) matchObject.path("players").get(0);
        playerObject.remove("assists");
        JsonNode player = MAPPER.convertValue(playerObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToPlayerFromDetailFilling(player, duration));
    }

    @Test
    @Order(17)
    public void
            testMapToPlayerFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoHealing() {
        Long duration = matchDTO.getGameDuration();
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode playerObject = (ObjectNode) matchObject.path("players").get(0);
        playerObject.remove("healing");
        JsonNode player = MAPPER.convertValue(playerObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToPlayerFromDetailFilling(player, duration));
    }

    @Test
    @Order(18)
    public void
            testMapToPlayerFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoDamageToPlayers() {
        Long duration = matchDTO.getGameDuration();
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode playerObject = (ObjectNode) matchObject.path("players").get(0);
        playerObject.remove("damageToPlayers");
        JsonNode player = MAPPER.convertValue(playerObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToPlayerFromDetailFilling(player, duration));
    }

    @Test
    @Order(19)
    public void
            testMapToPlayerFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoWards() {
        Long duration = matchDTO.getGameDuration();
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode playerObject = (ObjectNode) matchObject.path("players").get(0);
        playerObject.remove("wards");
        JsonNode player = MAPPER.convertValue(playerObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToPlayerFromDetailFilling(player, duration));
    }

    @Test
    @Order(20)
    public void
            testMapToPlayerFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoGold() {
        Long duration = matchDTO.getGameDuration();
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode playerObject = (ObjectNode) matchObject.path("players").get(0);
        playerObject.remove("gold");
        JsonNode player = MAPPER.convertValue(playerObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToPlayerFromDetailFilling(player, duration));
    }

    @Test
    @Order(21)
    public void
            testMapToPlayerFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoRole() {
        Long duration = matchDTO.getGameDuration();
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode playerObject = (ObjectNode) matchObject.path("players").get(0);
        playerObject.remove("role");
        JsonNode player = MAPPER.convertValue(playerObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToPlayerFromDetailFilling(player, duration));
    }

    @Test
    @Order(22)
    public void
            testMapToPlayerFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoChampion() {
        Long duration = matchDTO.getGameDuration();
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode playerObject = (ObjectNode) matchObject.path("players").get(0);
        playerObject.remove("champion");
        JsonNode player = MAPPER.convertValue(playerObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToPlayerFromDetailFilling(player, duration));
    }

    @Test
    @Order(23)
    public void
            testMapToPlayerFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoKilledMinions() {
        Long duration = matchDTO.getGameDuration();
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode playerObject = (ObjectNode) matchObject.path("players").get(0);
        playerObject.remove("killedMinions");
        JsonNode player = MAPPER.convertValue(playerObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToPlayerFromDetailFilling(player, duration));
    }

    @Test
    @Order(24)
    public void
            testMapToPlayerFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoTripleKills() {
        Long duration = matchDTO.getGameDuration();
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode playerObject = (ObjectNode) matchObject.path("players").get(0);
        playerObject.remove("tripleKills");
        JsonNode player = MAPPER.convertValue(playerObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToPlayerFromDetailFilling(player, duration));
    }

    @Test
    @Order(25)
    public void
            testMapToPlayerFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoQuadKills() {
        Long duration = matchDTO.getGameDuration();
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode playerObject = (ObjectNode) matchObject.path("players").get(0);
        playerObject.remove("quadKills");
        JsonNode player = MAPPER.convertValue(playerObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToPlayerFromDetailFilling(player, duration));
    }

    @Test
    @Order(26)
    public void
            testMapToPlayerFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoPentaKills() {
        Long duration = matchDTO.getGameDuration();
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode playerObject = (ObjectNode) matchObject.path("players").get(0);
        playerObject.remove("pentaKills");
        JsonNode player = MAPPER.convertValue(playerObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToPlayerFromDetailFilling(player, duration));
    }

    @Test
    @Order(27)
    public void
            testMapToPlayerFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoSide() {
        Long duration = matchDTO.getGameDuration();
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode playerObject = (ObjectNode) matchObject.path("players").get(0);
        playerObject.remove("side");
        JsonNode player = MAPPER.convertValue(playerObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToPlayerFromDetailFilling(player, duration));
    }

    @Test
    @Order(28)
    public void
            testMapToPlayerFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoWon() {
        Long duration = matchDTO.getGameDuration();
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode playerObject = (ObjectNode) matchObject.path("players").get(0);
        playerObject.remove("won");
        JsonNode player = MAPPER.convertValue(playerObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToPlayerFromDetailFilling(player, duration));
    }

    @Test
    @Order(29)
    public void
            testMapToPlayerFromDetailFilling_ShouldReturnPlayerStatsDTO_WhenDetailFillingPlayerHasAllNeededFields() {
        Long duration = matchDTO.getGameDuration();
        JsonNode player = match.get("players").get(0);
        assertEquals(
                playerStatsDTOList.getFirst(),
                detailFillingAdapterService.mapToPlayerFromDetailFilling(player, duration));
    }

    @Test
    @Order(30)
    public void
            testMapToTeamFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoSide() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode teamObject = (ObjectNode) matchObject.path("teams").get(0);
        teamObject.remove("side");
        JsonNode team = MAPPER.convertValue(teamObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToTeamFromDetailFilling(team));
    }

    @Test
    @Order(31)
    public void
            testMapToTeamFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoTeamKills() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode teamObject = (ObjectNode) matchObject.path("teams").get(0);
        teamObject.remove("teamKills");
        JsonNode team = MAPPER.convertValue(teamObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToTeamFromDetailFilling(team));
    }

    @Test
    @Order(32)
    public void
            testMapToTeamFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoTeamDeaths() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode teamObject = (ObjectNode) matchObject.path("teams").get(0);
        teamObject.remove("teamDeaths");
        JsonNode team = MAPPER.convertValue(teamObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToTeamFromDetailFilling(team));
    }

    @Test
    @Order(33)
    public void
            testMapToTeamFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoTeamAssists() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode teamObject = (ObjectNode) matchObject.path("teams").get(0);
        teamObject.remove("teamAssists");
        JsonNode team = MAPPER.convertValue(teamObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToTeamFromDetailFilling(team));
    }

    @Test
    @Order(34)
    public void
            testMapToTeamFromDetailFilling_ShouldThrowInvalidExternalJsonFormatException_WhenNoTeamHealing() {
        ObjectNode matchObject = (ObjectNode) match;
        ObjectNode teamObject = (ObjectNode) matchObject.path("teams").get(0);
        teamObject.remove("teamHealing");
        JsonNode team = MAPPER.convertValue(teamObject, JsonNode.class);
        assertThrows(
                InvalidExternalJsonFormatException.class,
                () -> detailFillingAdapterService.mapToTeamFromDetailFilling(team));
    }

    @Test
    @Order(35)
    public void
            testMapToTeamFromDetailFilling_ShouldReturnTeamsStatsDTO_WhenDetailFillingTeamHasAllNeededFields() {
        JsonNode team = match.get("teams").get(0);
        assertEquals(
                teamStatsDTOList.getFirst(), detailFillingAdapterService.mapToTeamFromDetailFilling(team));
    }
}
