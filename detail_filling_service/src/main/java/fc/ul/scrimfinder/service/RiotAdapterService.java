package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.player.PlayerDto;
import fc.ul.scrimfinder.exception.ExternalServiceUnavailableException;

public interface RiotAdapterService {
    String askForMatch(Long matchId) throws ExternalServiceUnavailableException;

    PlayerDto askForPlayer(String name, String tag) throws ExternalServiceUnavailableException;
}
