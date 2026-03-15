package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.client.RiotAccountServiceClient;
import fc.ul.scrimfinder.client.RiotMatchServiceClient;
import fc.ul.scrimfinder.client.RiotPlayerServiceClient;
import fc.ul.scrimfinder.dto.response.match.MatchStatsDTO;
import fc.ul.scrimfinder.dto.response.player.PlayerDTO;
import fc.ul.scrimfinder.exception.ExternalServiceUnavailableException;
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

    @Override
    public String getRawMatchData(Long matchId) throws ExternalServiceUnavailableException {
        return matchServiceClient.getMatch("EUW1_" + matchId);
    }

    @Override
    public MatchStatsDTO getMatchData(Long matchId) throws ExternalServiceUnavailableException {
        String match = matchServiceClient.getMatch("EUW1_" + matchId);
        return null;
    }

    @Override
    public PlayerDTO getPlayerData(String name, String tag) throws ExternalServiceUnavailableException {
        String account = accountServiceClient.getByRiotId(name, tag);
        // String playerQueueStats = playerServiceClient.getLeagueEntriesByPUUID(account.puuid());
        return null;
    }
}

