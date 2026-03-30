package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.PlayerDTO;
import fc.ul.scrimfinder.exception.LeagueAccountNotLinkedException;
import fc.ul.scrimfinder.exception.PlayerNotFoundException;
import fc.ul.scrimfinder.util.Region;
import java.util.UUID;

public interface PlayerService {

    PlayerDTO createPlayer(UUID id, String username);

    PlayerDTO getPlayer(UUID id) throws PlayerNotFoundException;

    PlayerDTO linkLolAccount(
            UUID playerId, String puuid, String gameName, String tagLine, Region region);

    PlayerDTO setPrimaryAccount(UUID playerId, String puuid);

    PlayerDTO syncPlayerMMR(UUID playerId)
            throws PlayerNotFoundException, LeagueAccountNotLinkedException;
}
