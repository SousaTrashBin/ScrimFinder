package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.dto.response.PlayerDto;
import fc.ul.scrimfinder.service.PlayerFillingService;
import fc.ul.scrimfinder.service.RiotAdapterService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PlayerFillingServiceImpl implements PlayerFillingService {

    @Inject
    RiotAdapterService riotAdapterService;

    @Override
    public PlayerDto getPlayerById(String playerId) {
        return null;
    }
}
