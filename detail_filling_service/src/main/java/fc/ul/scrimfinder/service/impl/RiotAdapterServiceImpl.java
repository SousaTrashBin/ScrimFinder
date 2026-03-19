package fc.ul.scrimfinder.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import fc.ul.scrimfinder.client.*;
import fc.ul.scrimfinder.dto.response.match.MatchStatsDTO;
import fc.ul.scrimfinder.dto.response.player.*;
import fc.ul.scrimfinder.exception.InvalidMatchFormatException;
import fc.ul.scrimfinder.exception.InvalidPlayerFormatException;
import fc.ul.scrimfinder.mapper.RiotMapper;
import fc.ul.scrimfinder.service.RiotAdapterService;
import fc.ul.scrimfinder.util.JsonNodeFinder;
import fc.ul.scrimfinder.util.Subregion;
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
    RiotRegionServiceClient regionServiceClient;

    @Inject
    @RestClient
    RiotSummonerServiceClient summonerServiceClient;

    @Inject
    ClientUrlPrefixProvider clientUrlPrefixProvider;

    @Override
    public String getRawMatchData(String matchId, Subregion subregion) {
        String region = subregion.toRegion().getRegionName();
        String subregionName = subregion.getSubRegionName().toUpperCase();
        clientUrlPrefixProvider.setPrefix(region);
        return matchServiceClient.getMatch(String.format("%s_%s", subregionName, matchId));
    }

    @Override
    public MatchStatsDTO getMatchData(String matchId, Subregion subregion) {
        return RiotMapper.toMatchStatsDTO(
                Objects.requireNonNull(new JsonNodeFinder(null)
                                .fromStringOrThrow(getRawMatchData(matchId, subregion), InvalidMatchFormatException.class))
                        .jsonNode()
        );
    }

    @Override
    public PlayerDTO getPlayerData(String name, String tag) {
        AccountDTO account = getAccountData(name, tag);
        RegionDTO region = getRegionData(account.puuid());
        SummonerDTO summoner = getSummonerData(account.puuid(), region.subregion());

        clientUrlPrefixProvider.setPrefix(region.subregion());
        String rawPlayerQueueStats = playerServiceClient.getLeagueEntriesByPUUID(account.puuid());
        JsonNode playerQueueStats = Objects.requireNonNull(new JsonNodeFinder(null)
                        .fromStringOrThrow(rawPlayerQueueStats, InvalidPlayerFormatException.class))
                .jsonNode();
        Set<PlayerQueueStatsDTO> queues = StreamSupport.stream(playerQueueStats.spliterator(), true)
                .map(RiotMapper::toPlayerQueueStatsDTO)
                .collect(Collectors.toSet());

        return new PlayerDTO(
                account,
                region,
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

    private RegionDTO getRegionData(String puuid) {
        String rawRegion = regionServiceClient.getActiveRegion(puuid);
        return RiotMapper.toRegionDTO(Objects.requireNonNull(new JsonNodeFinder(null)
                        .fromStringOrThrow(rawRegion, InvalidPlayerFormatException.class))
                .jsonNode()
        );
    }

    private SummonerDTO getSummonerData(String puuid, String subregion) {
        clientUrlPrefixProvider.setPrefix(subregion);
        String rawSummoner = summonerServiceClient.getByAccessToken(puuid);
        return RiotMapper.toSummonerDTO(Objects.requireNonNull(new JsonNodeFinder(null)
                        .fromStringOrThrow(rawSummoner, InvalidPlayerFormatException.class))
                .jsonNode()
        );
    }
}

