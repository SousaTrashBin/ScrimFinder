package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.client.RiotAccountServiceClient;
import fc.ul.scrimfinder.client.RiotMatchServiceClient;
import fc.ul.scrimfinder.client.RiotPlayerServiceClient;
import fc.ul.scrimfinder.dto.response.player.AccountDTO;
import fc.ul.scrimfinder.dto.response.player.PlayerDTO;
import fc.ul.scrimfinder.exception.ExternalServiceUnavailableException;
import fc.ul.scrimfinder.service.RiotAdapterService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class RiotAdapterServiceImpl implements RiotAdapterService {

    @Inject @RestClient RiotAccountServiceClient accountServiceClient;

    @Inject @RestClient RiotPlayerServiceClient playerServiceClient;

    @Inject @RestClient RiotMatchServiceClient matchServiceClient;

    @Override
    public String getMatchData(Long matchId) throws ExternalServiceUnavailableException {
        return matchServiceClient.getMatch("EUW1_" + matchId);
    }

    @Override
    public PlayerDTO getPlayerData(String name, String tag)
            throws ExternalServiceUnavailableException {
        AccountDTO account = accountServiceClient.getByRiotId(name, tag);
        return playerServiceClient.getLeagueEntriesByPUUID(account.puuid());
    }
}
