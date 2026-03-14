package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.player.PlayerDTO;
import fc.ul.scrimfinder.exception.ExternalServiceUnavailableException;
import fc.ul.scrimfinder.exception.PlayerNotFoundException;

public interface PlayerFillingService {
    PlayerDTO getFilledPlayer(String playerId) throws PlayerNotFoundException, ExternalServiceUnavailableException;
}
