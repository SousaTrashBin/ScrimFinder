package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.PlayerDTO;
import java.util.UUID;

public interface PlayerService {
    PlayerDTO createPlayer(UUID id, String discordUsername);

    PlayerDTO getPlayer(UUID id);
}
