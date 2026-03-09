package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.MatchDto;
import fc.ul.scrimfinder.dto.response.PlayerDto;
import fc.ul.scrimfinder.exception.ExternalServiceUnavailableException;

public interface RiotAdapterService {
    MatchDto askForMatch(Long matchId) throws ExternalServiceUnavailableException;

    PlayerDto askForPlayer(String name, String tag) throws ExternalServiceUnavailableException;
}
