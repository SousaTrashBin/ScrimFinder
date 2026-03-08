package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.PlayerDTO;

public interface PlayerService {
    PlayerDTO createPlayer(Long id, String username);

    PlayerDTO getPlayer(Long id);
}
