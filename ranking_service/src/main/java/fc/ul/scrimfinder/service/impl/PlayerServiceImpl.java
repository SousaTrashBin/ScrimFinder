package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.domain.Player;
import fc.ul.scrimfinder.dto.response.PlayerDTO;
import fc.ul.scrimfinder.dto.response.RankDTO;
import fc.ul.scrimfinder.exception.*;
import fc.ul.scrimfinder.mapper.PlayerMapper;
import fc.ul.scrimfinder.repository.PlayerRepository;
import fc.ul.scrimfinder.rest.client.ExternalPlayerClient;
import fc.ul.scrimfinder.service.PlayerService;
import fc.ul.scrimfinder.util.MMRConverter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class PlayerServiceImpl implements PlayerService {

    @Inject
    PlayerRepository playerRepository;

    @Inject
    PlayerMapper playerMapper;

    @Inject
    @RestClient
    ExternalPlayerClient externalPlayerClient;

    @Inject
    MMRConverter mmrConverter;

    @Override
    @Transactional
    public PlayerDTO createPlayer(Long id, String username) {
        if (playerRepository.find("discordUsername", username).count() > 0) {
            throw new PlayerAlreadyCreatedException("There's already a player created with that discord username");
        }
        Player player = new Player();
        player.setId(id);
        player.setDiscordUsername(username);
        playerRepository.persist(player);
        return playerMapper.toDTO(player);
    }

    @Override
    @Transactional
    public PlayerDTO linkLolAccount(Long playerId, String lolAccountId) {
        Player player = playerRepository.findByIdOptional(playerId)
                .orElseThrow(() -> new PlayerNotFoundException("Internal player not found"));

        try {
            externalPlayerClient.fetchPlayerRank(lolAccountId);
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                throw new ExternalAccountNotFoundException("The provided League of Legends account ID does not exist.");
            }
            throw new ExternalServiceUnavailableException("External service is currently unavailable. Please try again later.");
        }

        player.setLolAccountPPUID(lolAccountId);
        playerRepository.persist(player);

        return syncPlayerMMR(playerId);
    }

    @Override
    @Transactional
    public PlayerDTO syncPlayerMMR(Long playerId) throws PlayerNotFoundException, LeagueAccountNotLinkedException {
        Player player = playerRepository.findByIdOptional(playerId)
                .orElseThrow(() -> new PlayerNotFoundException("Player not found"));

        if (player.getLolAccountPPUID() == null) {
            throw new LeagueAccountNotLinkedException("Player must link a League of Legends account first");
        }

        var externalRank = externalPlayerClient.fetchPlayerRank(player.getLolAccountPPUID());

        RankDTO soloqRank = externalRank.rankDTOMap().get(fc.ul.scrimfinder.util.GameMode.SOLOQ);
        if (soloqRank != null) {
            player.setSoloqMMR(mmrConverter.convertRankToMMR(soloqRank));
        }

        RankDTO flexRank = externalRank.rankDTOMap().get(fc.ul.scrimfinder.util.GameMode.FLEX);
        if (flexRank != null) {
            player.setFlexMMR(mmrConverter.convertRankToMMR(flexRank));
        }

        playerRepository.persist(player);
        return playerMapper.toDTO(player);
    }
}
