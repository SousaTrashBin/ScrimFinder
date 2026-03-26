package fc.ul.scrimfinder.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import fc.ul.scrimfinder.dto.response.match.MatchStatsDTO;
import fc.ul.scrimfinder.dto.response.match.PlayerStatsDTO;
import fc.ul.scrimfinder.dto.response.match.TeamStatsDTO;
import fc.ul.scrimfinder.dto.response.player.AccountDTO;
import fc.ul.scrimfinder.dto.response.player.PlayerQueueStatsDTO;
import fc.ul.scrimfinder.dto.response.player.RegionDTO;
import fc.ul.scrimfinder.dto.response.player.SummonerDTO;
import fc.ul.scrimfinder.exception.InvalidMatchFormatException;
import fc.ul.scrimfinder.exception.InvalidPlayerFormatException;
import fc.ul.scrimfinder.exception.InvalidTeamFormatException;
import fc.ul.scrimfinder.util.*;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;

public class RiotMapper {
    public static MatchStatsDTO toMatchStatsDTO(JsonNode match) {
        String riotMatchId =
                new JsonNodeFinder(match)
                        .jsonGetOrThrow("metadata", InvalidMatchFormatException.class)
                        .jsonGetOrThrow("matchId", InvalidMatchFormatException.class)
                        .jsonNode()
                        .asText();

        JsonNodeFinder jsonNodeFinder =
                new JsonNodeFinder(match).jsonGetOrThrow("info", InvalidMatchFormatException.class);

        Long queueId =
                jsonNodeFinder
                        .jsonGetOrThrow("queueId", InvalidMatchFormatException.class)
                        .jsonNode()
                        .asLong();

        String gameVersion =
                jsonNodeFinder
                        .jsonGetOrThrow("gameVersion", InvalidMatchFormatException.class)
                        .jsonNode()
                        .asText();
        String[] gameVersionElements = gameVersion.split("\\.");
        if (gameVersionElements.length < 2) {
            throw new InvalidMatchFormatException(
                    "Not enough elements in gameVersion field of Riot response: " + gameVersion);
        }
        String patch = String.format("%s.%s", gameVersionElements[0], gameVersionElements[1]);

        Long gameCreation =
                jsonNodeFinder
                        .jsonGetOrThrow("gameStartTimestamp", InvalidMatchFormatException.class)
                        .jsonNode()
                        .asLong();

        Long gameDuration =
                jsonNodeFinder
                        .jsonGetOrThrow("gameDuration", InvalidMatchFormatException.class)
                        .jsonNode()
                        .asLong();

        JsonNode participants =
                jsonNodeFinder.jsonGetOrThrow("participants", InvalidMatchFormatException.class).jsonNode();
        List<PlayerStatsDTO> players =
                StreamSupport.stream(participants.spliterator(), true)
                        .map(RiotMapper::toPlayerStatsDTO)
                        .toList();

        JsonNode teamsNode =
                jsonNodeFinder.jsonGetOrThrow("teams", InvalidMatchFormatException.class).jsonNode();
        List<TeamStatsDTO> teams =
                StreamSupport.stream(teamsNode.spliterator(), true)
                        .map(team -> toTeamStatsDTO(team, players))
                        .toList();

        return new MatchStatsDTO(
                riotMatchId, queueId, patch, gameCreation, gameDuration, players, teams);
    }

    public static PlayerStatsDTO toPlayerStatsDTO(JsonNode player) {
        JsonNodeFinder playerNodeFinder = new JsonNodeFinder(player);

        String playerName =
                playerNodeFinder
                        .jsonGetOrThrow("riotIdGameName", InvalidPlayerFormatException.class)
                        .jsonNode()
                        .asText();

        String playerTag =
                playerNodeFinder
                        .jsonGetOrThrow("riotIdTagline", InvalidPlayerFormatException.class)
                        .jsonNode()
                        .asText();

        Integer playerIcon =
                playerNodeFinder
                        .jsonGetOrThrow("profileIcon", InvalidPlayerFormatException.class)
                        .jsonNode()
                        .asInt();

        RiotId riotId = new RiotId(playerName, playerTag, playerIcon);

        Integer kills =
                playerNodeFinder
                        .jsonGetOrThrow("kills", InvalidPlayerFormatException.class)
                        .jsonNode()
                        .asInt();

        Integer deaths =
                playerNodeFinder
                        .jsonGetOrThrow("deaths", InvalidPlayerFormatException.class)
                        .jsonNode()
                        .asInt();

        Integer assists =
                playerNodeFinder
                        .jsonGetOrThrow("assists", InvalidPlayerFormatException.class)
                        .jsonNode()
                        .asInt();

        Integer healing =
                playerNodeFinder
                        .jsonGetOrThrow("totalHeal", InvalidPlayerFormatException.class)
                        .jsonNode()
                        .asInt();

        Integer damageToPlayers =
                playerNodeFinder
                        .jsonGetOrThrow("totalDamageDealt", InvalidPlayerFormatException.class)
                        .jsonNode()
                        .asInt();

        Integer wards =
                playerNodeFinder
                        .jsonGetOrThrow("wardsPlaced", InvalidPlayerFormatException.class)
                        .jsonNode()
                        .asInt();

        Integer gold =
                playerNodeFinder
                        .jsonGetOrThrow("goldEarned", InvalidPlayerFormatException.class)
                        .jsonNode()
                        .asInt();

        Role role =
                Role.fromRoleName(
                        playerNodeFinder
                                .jsonGetOrThrow("teamPosition", InvalidPlayerFormatException.class)
                                .jsonNode()
                                .asText());

        String champion =
                playerNodeFinder
                        .jsonGetOrThrow("championName", InvalidPlayerFormatException.class)
                        .jsonNode()
                        .asText();

        Integer killedMinions =
                playerNodeFinder
                        .jsonGetOrThrow("totalMinionsKilled", InvalidPlayerFormatException.class)
                        .jsonNode()
                        .asInt();

        Integer tripleKills =
                playerNodeFinder
                        .jsonGetOrThrow("tripleKills", InvalidPlayerFormatException.class)
                        .jsonNode()
                        .asInt();

        Integer quadKills =
                playerNodeFinder
                        .jsonGetOrThrow("quadraKills", InvalidPlayerFormatException.class)
                        .jsonNode()
                        .asInt();

        Integer pentaKills =
                playerNodeFinder
                        .jsonGetOrThrow("pentaKills", InvalidPlayerFormatException.class)
                        .jsonNode()
                        .asInt();

        int teamId =
                playerNodeFinder
                        .jsonGetOrThrow("teamId", InvalidPlayerFormatException.class)
                        .jsonNode()
                        .asInt();
        TeamSide side =
                switch (teamId) {
                    case 100 -> TeamSide.BLUE;
                    case 200 -> TeamSide.RED;
                    default ->
                            throw new InvalidPlayerFormatException(
                                    "Invalid team id from Riot response. Should be 100 or 200. Got: " + teamId);
                };

        Boolean won =
                playerNodeFinder
                        .jsonGetOrThrow("win", InvalidPlayerFormatException.class)
                        .jsonNode()
                        .asBoolean();

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
                killedMinions,
                tripleKills,
                quadKills,
                pentaKills,
                side,
                won);
    }

    public static TeamStatsDTO toTeamStatsDTO(JsonNode team, List<PlayerStatsDTO> players) {
        JsonNodeFinder teamNodeFinder = new JsonNodeFinder(team);

        int teamId =
                teamNodeFinder
                        .jsonGetOrThrow("teamId", InvalidTeamFormatException.class)
                        .jsonNode()
                        .asInt();
        TeamSide side =
                switch (teamId) {
                    case 100 -> TeamSide.BLUE;
                    case 200 -> TeamSide.RED;
                    default ->
                            throw new InvalidTeamFormatException(
                                    "Invalid team id from Riot response. Should be 100 or 200. Got: " + teamId);
                };

        List<PlayerStatsDTO> teamPlayers =
                players.stream().filter(player -> player.side().equals(side)).toList();

        Integer teamKills = teamPlayers.stream().map(PlayerStatsDTO::kills).reduce(0, Integer::sum);
        Integer teamDeaths = teamPlayers.stream().map(PlayerStatsDTO::deaths).reduce(0, Integer::sum);
        Integer teamAssists = teamPlayers.stream().map(PlayerStatsDTO::assists).reduce(0, Integer::sum);
        Integer teamHealing = teamPlayers.stream().map(PlayerStatsDTO::healing).reduce(0, Integer::sum);

        return new TeamStatsDTO(side, teamKills, teamDeaths, teamAssists, teamHealing);
    }

    public static AccountDTO toAccountDTO(JsonNode account) {
        JsonNodeFinder accountNodeFinder = new JsonNodeFinder(account);

        String puuid =
                accountNodeFinder
                        .jsonGetOrThrow("puuid", InvalidPlayerFormatException.class)
                        .jsonNode()
                        .asText();

        String name =
                accountNodeFinder
                        .jsonGetOrThrow("gameName", InvalidPlayerFormatException.class)
                        .jsonNode()
                        .asText();

        String tag =
                accountNodeFinder
                        .jsonGetOrThrow("tagLine", InvalidPlayerFormatException.class)
                        .jsonNode()
                        .asText();

        return new AccountDTO(puuid, name, tag);
    }

    public static RegionDTO toRegionDTO(JsonNode region) {
        JsonNodeFinder regionNodeFinder = new JsonNodeFinder(region);

        String subregion =
                regionNodeFinder
                        .jsonGetOrThrow("region", InvalidPlayerFormatException.class)
                        .jsonNode()
                        .asText();

        String regionStr =
                Objects.requireNonNull(Subregion.fromSubregionName(subregion)).toRegion().getRegionName();

        return new RegionDTO(regionStr, subregion);
    }

    public static SummonerDTO toSummonerDTO(JsonNode summoner) {
        JsonNodeFinder summonerNodeFinder = new JsonNodeFinder(summoner);

        Integer icon =
                summonerNodeFinder
                        .jsonGetOrThrow("profileIconId", InvalidPlayerFormatException.class)
                        .jsonNode()
                        .asInt();

        Long level =
                summonerNodeFinder
                        .jsonGetOrThrow("summonerLevel", InvalidPlayerFormatException.class)
                        .jsonNode()
                        .asLong();

        return new SummonerDTO(icon, level);
    }

    public static PlayerQueueStatsDTO toPlayerQueueStatsDTO(JsonNode queue) {
        JsonNodeFinder queueFinder = new JsonNodeFinder(queue);

        String queueType =
                queueFinder
                        .jsonGetOrThrow("queueType", InvalidPlayerFormatException.class)
                        .jsonNode()
                        .asText();

        Rank rank = toRank(queue);

        Integer wins =
                queueFinder.jsonGetOrThrow("wins", InvalidPlayerFormatException.class).jsonNode().asInt();

        Integer losses =
                queueFinder.jsonGetOrThrow("losses", InvalidPlayerFormatException.class).jsonNode().asInt();

        Boolean hotStreak =
                queueFinder
                        .jsonGetOrThrow("hotStreak", InvalidPlayerFormatException.class)
                        .jsonNode()
                        .asBoolean();

        return new PlayerQueueStatsDTO(queueType, rank, wins, losses, hotStreak);
    }

    public static Rank toRank(JsonNode queue) {
        JsonNodeFinder queueFinder = new JsonNodeFinder(queue);

        Tier tier =
                Tier.fromTierName(
                        queueFinder
                                .jsonGetOrThrow("tier", InvalidPlayerFormatException.class)
                                .jsonNode()
                                .asText());

        Integer division =
                Objects.requireNonNull(
                                Division.fromDivisionName(
                                        queueFinder
                                                .jsonGetOrThrow("rank", InvalidPlayerFormatException.class)
                                                .jsonNode()
                                                .asText()))
                        .getDivisionInt();

        Integer lps =
                queueFinder
                        .jsonGetOrThrow("leaguePoints", InvalidPlayerFormatException.class)
                        .jsonNode()
                        .asInt();

        return new Rank(tier, division, lps);
    }
}
