package fc.ul.scrimfinder.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import fc.ul.scrimfinder.client.*;
import fc.ul.scrimfinder.dto.response.match.MatchStatsDTO;
import fc.ul.scrimfinder.dto.response.player.*;
import fc.ul.scrimfinder.exception.InvalidMatchFormatException;
import fc.ul.scrimfinder.exception.InvalidPlayerFormatException;
import fc.ul.scrimfinder.mapper.RiotMapper;
import fc.ul.scrimfinder.service.RiotAdapterService;
import fc.ul.scrimfinder.util.ColoredMessage;
import fc.ul.scrimfinder.util.JsonNodeFinder;
import fc.ul.scrimfinder.util.LogColor;
import fc.ul.scrimfinder.util.Subregion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

@ApplicationScoped
public class RiotAdapterServiceImpl implements RiotAdapterService {

    @Inject @RestClient RiotAccountServiceClient accountServiceClient;

    @Inject @RestClient RiotPlayerServiceClient playerServiceClient;

    @Inject @RestClient RiotMatchServiceClient matchServiceClient;

    @Inject @RestClient RiotSummonerServiceClient summonerServiceClient;

    @Inject ClientUrlPrefixProvider clientUrlPrefixProvider;

    @Inject Logger logger;

    @Override
    public String getRawMatchData(String matchId) {
        String[] idParts = matchId.split("_");
        if (idParts.length < 2) {
            logger.error(
                    ColoredMessage.withColor(
                            "Invalid match id format. It should be SUBREGION_ID but got: " + matchId,
                            LogColor.RED));
            throw new InvalidMatchFormatException(
                    "Invalid match id format. It should be SUBREGION_ID but got: " + matchId);
        }
        Subregion subregion = Subregion.fromSubregionName(idParts[0]);
        if (subregion == null) {
            logger.error(
                    ColoredMessage.withColor(
                            "Invalid match id. No subregion found for: " + idParts[0], LogColor.RED));
            throw new InvalidMatchFormatException(
                    "Invalid match id. No subregion found for: " + idParts[0]);
        }
        String region = subregion.toRegion().getRegionName();
        clientUrlPrefixProvider.setPrefix(region);
        return matchServiceClient.getMatch(matchId);
    }

    @Override
    public MatchStatsDTO getMatchData(String matchId) {
        return RiotMapper.toMatchStatsDTO(
                Objects.requireNonNull(
                                new JsonNodeFinder(null)
                                        .fromStringOrThrow(getRawMatchData(matchId), InvalidMatchFormatException.class))
                        .jsonNode());
    }

    @Override
    public PlayerDTO getPlayerData(String server, String name, String tag) {
        Subregion subregion = Subregion.fromServerName(server);
        if (subregion == null) {
            throw new InvalidPlayerFormatException("Invalid server: " + server);
        }

        AccountDTO account = getAccountData(name, tag, subregion.toRegion().getRegionName());
        RegionDTO region =
                new RegionDTO(subregion.toRegion().getRegionName(), subregion.getSubRegionName());
        SummonerDTO summoner = getSummonerData(account.puuid(), region.subregion());

        logger.info(
                ColoredMessage.withColor(
                        String.format("Fetch player data for player %s", account.puuid()), LogColor.GREEN));
        clientUrlPrefixProvider.setPrefix(region.subregion());
        String rawPlayerQueueStats = playerServiceClient.getLeagueEntriesByPUUID(account.puuid());
        JsonNode playerQueueStats =
                Objects.requireNonNull(
                                new JsonNodeFinder(null)
                                        .fromStringOrThrow(rawPlayerQueueStats, InvalidPlayerFormatException.class))
                        .jsonNode();
        Set<PlayerQueueStatsDTO> queues =
                StreamSupport.stream(playerQueueStats.spliterator(), true)
                        .map(RiotMapper::toPlayerQueueStatsDTO)
                        .collect(Collectors.toSet());

        return new PlayerDTO(account, region, summoner, queues);
    }

    private AccountDTO getAccountData(String name, String tag, String region) {
        logger.info(
                ColoredMessage.withColor(
                        String.format("Fetch account data for player %s#%s in region %s", name, tag, region),
                        LogColor.GREEN));
        clientUrlPrefixProvider.setPrefix(region);
        String rawAccount = accountServiceClient.getByRiotId(name, tag);
        return RiotMapper.toAccountDTO(
                Objects.requireNonNull(
                                new JsonNodeFinder(null)
                                        .fromStringOrThrow(rawAccount, InvalidPlayerFormatException.class))
                        .jsonNode());
    }

    private SummonerDTO getSummonerData(String puuid, String subregion) {
        logger.info(
                ColoredMessage.withColor(
                        String.format("Fetch summoner data for player %s", puuid), LogColor.GREEN));
        clientUrlPrefixProvider.setPrefix(subregion);
        String rawSummoner = summonerServiceClient.getByAccessToken(puuid);
        return RiotMapper.toSummonerDTO(
                Objects.requireNonNull(
                                new JsonNodeFinder(null)
                                        .fromStringOrThrow(rawSummoner, InvalidPlayerFormatException.class))
                        .jsonNode());
    }
}
