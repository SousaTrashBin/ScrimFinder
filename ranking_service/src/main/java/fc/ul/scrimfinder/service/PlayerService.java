package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.PlayerDTO;
import fc.ul.scrimfinder.exception.LeagueAccountNotLinkedException;
import fc.ul.scrimfinder.exception.PlayerNotFoundException;

public interface PlayerService {

    PlayerDTO createPlayer(Long id, String username);

    PlayerDTO linkLolAccount(Long playerId, String lolAccountId);


    PlayerDTO syncPlayerMMR(Long playerId) throws PlayerNotFoundException, LeagueAccountNotLinkedException;
}
