package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.player.PlayerDTO;
import fc.ul.scrimfinder.exception.ExternalServiceUnavailableException;

public interface RiotAdapterService {
    String getMatchData(Long matchId) throws ExternalServiceUnavailableException;

    PlayerDTO getPlayerData(String name, String tag) throws ExternalServiceUnavailableException;
}
