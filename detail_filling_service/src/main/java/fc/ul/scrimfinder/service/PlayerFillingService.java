package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.PlayerDto;
import fc.ul.scrimfinder.exception.ExternalServiceUnavailableException;
import fc.ul.scrimfinder.exception.PlayerNotFoundException;

public interface PlayerFillingService {
    PlayerDto getPlayerById(String playerId) throws PlayerNotFoundException, ExternalServiceUnavailableException;
}
