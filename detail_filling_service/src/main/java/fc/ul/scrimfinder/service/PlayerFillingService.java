package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.player.PlayerDTO;
import fc.ul.scrimfinder.exception.*;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;

public interface PlayerFillingService {
    @CircuitBreaker(skipOn = {PlayerNotFoundException.class})
    PlayerDTO getFilledPlayer(String server, String name, String tag)
            throws PlayerNotFoundException,
                    InvalidPlayerFormatException,
                    UnauthorizedException,
                    ExternalServiceUnavailableException;
}
