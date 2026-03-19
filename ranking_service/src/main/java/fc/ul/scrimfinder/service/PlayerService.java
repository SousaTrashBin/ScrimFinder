package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.PlayerDTO;
import fc.ul.scrimfinder.exception.LeagueAccountNotLinkedException;
import fc.ul.scrimfinder.exception.PlayerNotFoundException;
import fc.ul.scrimfinder.util.Region;

public interface PlayerService {

    PlayerDTO createPlayer(Long id, String username);

    PlayerDTO linkLolAccount(
            Long playerId, String puuid, String gameName, String tagLine, Region region);

    PlayerDTO setPrimaryAccount(Long playerId, String puuid);

    PlayerDTO syncPlayerMMR(Long playerId)
            throws PlayerNotFoundException, LeagueAccountNotLinkedException;
}
