package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.dto.response.match.MatchDto;
import fc.ul.scrimfinder.dto.response.player.PlayerDto;
import fc.ul.scrimfinder.exception.ExternalServiceUnavailableException;
import fc.ul.scrimfinder.service.RiotAdapterService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RiotAdapterServiceImpl implements RiotAdapterService {
    @Override
    public MatchDto askForMatch(Long matchId) throws ExternalServiceUnavailableException {
        return null;
    }

    @Override
    public PlayerDto askForPlayer(String name, String tag) throws ExternalServiceUnavailableException {
        return null;
    }
}
