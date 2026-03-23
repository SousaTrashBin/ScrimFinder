package fc.ul.scrimfinder.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import fc.ul.scrimfinder.client.DetailFillingClient;
import fc.ul.scrimfinder.dto.request.PlayerStatsDTO;
import fc.ul.scrimfinder.dto.request.TeamStatsDTO;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.exception.InvalidExternalJsonFormatException;
import fc.ul.scrimfinder.service.DetailFillingAdapterService;
import fc.ul.scrimfinder.util.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class DetailFillingAdapterServiceImpl implements DetailFillingAdapterService {
    @Inject @RestClient DetailFillingClient detailFillingClient;

    @Override
    public MatchDTO getMatch(String riotMatchId) {
        return mapToMatchFromDetailFilling(detailFillingClient.getFilledMatch(riotMatchId));
    }

    @Override
    public String getPlayerPuuid(String name, String tag) {
        return mapToPlayerPuuidFromDetailFilling(detailFillingClient.getFilledPlayer(name, tag));
    }

    @Override
    public Integer getPlayerIcon(String name, String tag) {
        return mapToPlayerIconFromDetailFilling(detailFillingClient.getFilledPlayer(name, tag));
    }

    private MatchDTO mapToMatchFromDetailFilling(String json) {
        JsonNodeFinder matchFinder =
                Objects.requireNonNull(
                        new JsonNodeFinder(null)
                                .fromStringOrThrow(json, InvalidExternalJsonFormatException.class));

        String riotMatchId =
                matchFinder
                        .jsonGetOrThrow("riotMatchId", InvalidExternalJsonFormatException.class)
                        .jsonNode()
                        .asText();

        Long queueId =
                matchFinder
                        .jsonGetOrThrow("queueId", InvalidExternalJsonFormatException.class)
                        .jsonNode()
                        .asLong();

        String patch =
                matchFinder
                        .jsonGetOrThrow("patch", InvalidExternalJsonFormatException.class)
                        .jsonNode()
                        .asText();

        LocalDateTime gameCreation =
                LocalDateTime.parse(
                        matchFinder
                                .jsonGetOrThrow("gameCreation", InvalidExternalJsonFormatException.class)
                                .jsonNode()
                                .asText());

        Long gameDuration =
                matchFinder
                        .jsonGetOrThrow("gameDuration", InvalidExternalJsonFormatException.class)
                        .jsonNode()
                        .asLong();

        JsonNode participants =
                matchFinder.jsonGetOrThrow("players", InvalidExternalJsonFormatException.class).jsonNode();
        List<PlayerStatsDTO> players =
                StreamSupport.stream(participants.spliterator(), true)
                        .map(node -> mapToPlayerFromDetailFilling(node, gameDuration))
                        .toList();

        JsonNode teamsNode =
                matchFinder.jsonGetOrThrow("teams", InvalidExternalJsonFormatException.class).jsonNode();
        List<TeamStatsDTO> teams =
                StreamSupport.stream(teamsNode.spliterator(), true)
                        .map(this::mapToTeamFromDetailFilling)
                        .toList();

        return new MatchDTO(riotMatchId, queueId, patch, gameCreation, gameDuration, players, teams);
    }

    private PlayerStatsDTO mapToPlayerFromDetailFilling(JsonNode player, Long gameDuration) {
        JsonNodeFinder playerNodeFinder = new JsonNodeFinder(player);

        JsonNodeFinder riotIdNodeFinder =
                playerNodeFinder.jsonGetOrThrow("riotId", InvalidExternalJsonFormatException.class);

        String playerName =
                riotIdNodeFinder
                        .jsonGetOrThrow("playerName", InvalidExternalJsonFormatException.class)
                        .jsonNode()
                        .asText();

        String playerTag =
                riotIdNodeFinder
                        .jsonGetOrThrow("playerTag", InvalidExternalJsonFormatException.class)
                        .jsonNode()
                        .asText();

        Integer playerIcon =
                riotIdNodeFinder
                        .jsonGetOrThrow("playerIcon", InvalidExternalJsonFormatException.class)
                        .jsonNode()
                        .asInt();

        RiotId riotId = new RiotId(playerName, playerTag, playerIcon);

        Integer kills =
                playerNodeFinder
                        .jsonGetOrThrow("kills", InvalidExternalJsonFormatException.class)
                        .jsonNode()
                        .asInt();

        Integer deaths =
                playerNodeFinder
                        .jsonGetOrThrow("deaths", InvalidExternalJsonFormatException.class)
                        .jsonNode()
                        .asInt();

        Integer assists =
                playerNodeFinder
                        .jsonGetOrThrow("assists", InvalidExternalJsonFormatException.class)
                        .jsonNode()
                        .asInt();

        Integer healing =
                playerNodeFinder
                        .jsonGetOrThrow("healing", InvalidExternalJsonFormatException.class)
                        .jsonNode()
                        .asInt();

        Integer damageToPlayers =
                playerNodeFinder
                        .jsonGetOrThrow("damageToPlayers", InvalidExternalJsonFormatException.class)
                        .jsonNode()
                        .asInt();

        Integer wards =
                playerNodeFinder
                        .jsonGetOrThrow("wards", InvalidExternalJsonFormatException.class)
                        .jsonNode()
                        .asInt();

        Integer gold =
                playerNodeFinder
                        .jsonGetOrThrow("gold", InvalidExternalJsonFormatException.class)
                        .jsonNode()
                        .asInt();

        Role role =
                Role.fromRoleName(
                        playerNodeFinder
                                .jsonGetOrThrow("role", InvalidExternalJsonFormatException.class)
                                .jsonNode()
                                .asText());

        Champion champion =
                Champion.fromChampionName(
                        playerNodeFinder
                                .jsonGetOrThrow("champion", InvalidExternalJsonFormatException.class)
                                .jsonNode()
                                .asText());

        Integer killedMinions =
                playerNodeFinder
                        .jsonGetOrThrow("killedMinions", InvalidExternalJsonFormatException.class)
                        .jsonNode()
                        .asInt();

        Double csPerMinute = killedMinions / TimeConverter.millisecondsToMinutes(gameDuration);

        Integer tripleKills =
                playerNodeFinder
                        .jsonGetOrThrow("tripleKills", InvalidExternalJsonFormatException.class)
                        .jsonNode()
                        .asInt();

        Integer quadKills =
                playerNodeFinder
                        .jsonGetOrThrow("quadKills", InvalidExternalJsonFormatException.class)
                        .jsonNode()
                        .asInt();

        Integer pentaKills =
                playerNodeFinder
                        .jsonGetOrThrow("pentaKills", InvalidExternalJsonFormatException.class)
                        .jsonNode()
                        .asInt();

        TeamSide side =
                TeamSide.fromTeamSideName(
                        playerNodeFinder
                                .jsonGetOrThrow("side", InvalidExternalJsonFormatException.class)
                                .jsonNode()
                                .asText());

        Boolean won =
                playerNodeFinder
                        .jsonGetOrThrow("won", InvalidExternalJsonFormatException.class)
                        .jsonNode()
                        .asBoolean();

        Integer mmrDelta = null;

        return new PlayerStatsDTO(
                riotId,
                kills,
                deaths,
                assists,
                healing,
                damageToPlayers,
                wards,
                gold,
                role,
                champion,
                csPerMinute,
                killedMinions,
                tripleKills,
                quadKills,
                pentaKills,
                side,
                won,
                mmrDelta);
    }

    private TeamStatsDTO mapToTeamFromDetailFilling(JsonNode team) {
        JsonNodeFinder teamNodeFinder = new JsonNodeFinder(team);

        TeamSide side =
                TeamSide.fromTeamSideName(
                        teamNodeFinder
                                .jsonGetOrThrow("side", InvalidExternalJsonFormatException.class)
                                .jsonNode()
                                .asText());

        Integer teamKills =
                teamNodeFinder
                        .jsonGetOrThrow("teamKills", InvalidExternalJsonFormatException.class)
                        .jsonNode()
                        .asInt();

        Integer teamDeaths =
                teamNodeFinder
                        .jsonGetOrThrow("teamDeaths", InvalidExternalJsonFormatException.class)
                        .jsonNode()
                        .asInt();

        Integer teamAssists =
                teamNodeFinder
                        .jsonGetOrThrow("teamAssists", InvalidExternalJsonFormatException.class)
                        .jsonNode()
                        .asInt();

        Integer teamHealing =
                teamNodeFinder
                        .jsonGetOrThrow("teamHealing", InvalidExternalJsonFormatException.class)
                        .jsonNode()
                        .asInt();

        return new TeamStatsDTO(side, teamKills, teamDeaths, teamAssists, teamHealing);
    }

    private String mapToPlayerPuuidFromDetailFilling(String json) {
        return Objects.requireNonNull(
                        new JsonNodeFinder(null)
                                .fromStringOrThrow(json, InvalidExternalJsonFormatException.class))
                .jsonGetOrThrow("account", InvalidExternalJsonFormatException.class)
                .jsonGetOrThrow("puuid", InvalidExternalJsonFormatException.class)
                .jsonNode()
                .asText();
    }

    private Integer mapToPlayerIconFromDetailFilling(String json) {
        return Objects.requireNonNull(
                        new JsonNodeFinder(null)
                                .fromStringOrThrow(json, InvalidExternalJsonFormatException.class))
                .jsonGetOrThrow("summoner", InvalidExternalJsonFormatException.class)
                .jsonGetOrThrow("icon", InvalidExternalJsonFormatException.class)
                .jsonNode()
                .asInt();
    }
}
