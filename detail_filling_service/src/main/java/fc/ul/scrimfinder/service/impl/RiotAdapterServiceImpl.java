package fc.ul.scrimfinder.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import fc.ul.scrimfinder.Config;
import fc.ul.scrimfinder.client.RiotAccountServiceClient;
import fc.ul.scrimfinder.client.RiotMatchServiceClient;
import fc.ul.scrimfinder.client.RiotPlayerServiceClient;
import fc.ul.scrimfinder.client.RiotSummonerServiceClient;
import fc.ul.scrimfinder.dto.response.match.MatchStatsDTO;
import fc.ul.scrimfinder.dto.response.player.AccountDTO;
import fc.ul.scrimfinder.dto.response.player.PlayerDTO;
import fc.ul.scrimfinder.dto.response.player.PlayerQueueStatsDTO;
import fc.ul.scrimfinder.dto.response.player.SummonerDTO;
import fc.ul.scrimfinder.exception.InvalidMatchFormatException;
import fc.ul.scrimfinder.exception.InvalidPlayerFormatException;
import fc.ul.scrimfinder.mapper.RiotMapper;
import fc.ul.scrimfinder.service.RiotAdapterService;
import fc.ul.scrimfinder.util.JsonNodeFinder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@ApplicationScoped
public class RiotAdapterServiceImpl implements RiotAdapterService {

    @Inject
    @RestClient
    RiotAccountServiceClient accountServiceClient;

    @Inject
    @RestClient
    RiotPlayerServiceClient playerServiceClient;

    @Inject
    @RestClient
    RiotMatchServiceClient matchServiceClient;

    @Inject
    @RestClient
    RiotSummonerServiceClient summonerServiceClient;

    @Inject
    Config config;

    @Override
    public String getRawMatchData(String matchId) {
        return matchServiceClient.getMatch(String.format("%s_%s", config.riotApiSubregion(), matchId));
    }

    @Override
    public MatchStatsDTO getMatchData(String matchId) {
        return RiotMapper.toMatchStatsDTO(
                Objects.requireNonNull(new JsonNodeFinder(null)
                                .fromStringOrThrow(getRawMatchData(matchId), InvalidMatchFormatException.class))
                        .jsonNode()
        );
    }

    @Override
    public PlayerDTO getPlayerData(String name, String tag) {
        AccountDTO account = getAccountData(name, tag);
        SummonerDTO summoner = getSummonerData(account.puuid());

        String rawPlayerQueueStats = playerServiceClient.getLeagueEntriesByPUUID(account.puuid());
        JsonNode playerQueueStats = Objects.requireNonNull(new JsonNodeFinder(null)
                        .fromStringOrThrow(rawPlayerQueueStats, InvalidPlayerFormatException.class))
                .jsonNode();
        Set<PlayerQueueStatsDTO> queues = StreamSupport.stream(playerQueueStats.spliterator(), true)
                .map(RiotMapper::toPlayerQueueStatsDTO)
                .collect(Collectors.toSet());

        return new PlayerDTO(
                account,
                summoner,
                queues
        );
    }

    private AccountDTO getAccountData(String name, String tag) {
        String rawAccount = accountServiceClient.getByRiotId(name, tag);
        return RiotMapper.toAccountDTO(Objects.requireNonNull(new JsonNodeFinder(null)
                        .fromStringOrThrow(rawAccount, InvalidPlayerFormatException.class))
                .jsonNode()
        );
    }

    private SummonerDTO getSummonerData(String puuid) {
        String rawSummoner = summonerServiceClient.getByAccessToken(puuid);
        return RiotMapper.toSummonerDTO(Objects.requireNonNull(new JsonNodeFinder(null)
                        .fromStringOrThrow(rawSummoner, InvalidPlayerFormatException.class))
                .jsonNode()
        );
    }
}

