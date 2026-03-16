package fc.ul.scrimfinder.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fc.ul.scrimfinder.Config;
import fc.ul.scrimfinder.client.RiotAccountServiceClient;
import fc.ul.scrimfinder.client.RiotMatchServiceClient;
import fc.ul.scrimfinder.client.RiotPlayerServiceClient;
import fc.ul.scrimfinder.dto.response.match.MatchStatsDTO;
import fc.ul.scrimfinder.dto.response.player.PlayerDTO;
import fc.ul.scrimfinder.exception.InvalidMatchFormatException;
import fc.ul.scrimfinder.mapper.RiotMapper;
import fc.ul.scrimfinder.service.RiotAdapterService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

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
    Config config;

    @Override
    public String getRawMatchData(String matchId) {
        return matchServiceClient.getMatch(String.format("%s_%s", config.riotApiSubregion(), matchId), config.riotApiKey());
    }

    @Override
    public MatchStatsDTO getMatchData(String matchId) {
        String rawMatch = getRawMatchData(matchId);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode match;
        try {
            match = mapper.readTree(rawMatch);
        } catch (Exception x) {
            throw new InvalidMatchFormatException(rawMatch);
        }
        return RiotMapper.toMatchStatsDTO(match);
    }

    @Override
    public PlayerDTO getPlayerData(String name, String tag) {
        String account = accountServiceClient.getByRiotId(name, tag);
        // String playerQueueStats = playerServiceClient.getLeagueEntriesByPUUID(account.puuid());
        return null;
    }
}

