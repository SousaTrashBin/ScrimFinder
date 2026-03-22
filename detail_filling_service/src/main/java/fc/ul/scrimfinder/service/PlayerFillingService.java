package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.player.PlayerDTO;
import fc.ul.scrimfinder.exception.ExternalServiceUnavailableException;
import fc.ul.scrimfinder.exception.InvalidPlayerFormatException;
import fc.ul.scrimfinder.exception.PlayerNotFoundException;
import fc.ul.scrimfinder.exception.UnauthorizedException;

public interface PlayerFillingService {
    PlayerDTO getFilledPlayer(String name, String tag)
            throws PlayerNotFoundException,
                    InvalidPlayerFormatException,
                    UnauthorizedException,
                    ExternalServiceUnavailableException;
}
