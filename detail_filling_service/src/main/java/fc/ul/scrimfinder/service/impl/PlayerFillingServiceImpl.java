package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.dto.response.player.PlayerDTO;
import fc.ul.scrimfinder.redis.RedisService;
import fc.ul.scrimfinder.service.PlayerFillingService;
import fc.ul.scrimfinder.service.RiotAdapterService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PlayerFillingServiceImpl implements PlayerFillingService {

    @Inject RiotAdapterService riotAdapterService;

    @Inject
    RedisService redisService;

    @Override
    public PlayerDTO getFilledPlayer(String name, String tag) {
        String playerRedisKey = String.format("%s#%s", name, tag);
        return redisService
                .get(playerRedisKey, PlayerDTO.class)
                .orElseGet(
                        () -> {
                            PlayerDTO player = riotAdapterService.getPlayerData(name, tag);
                            redisService.set(playerRedisKey, player);
                            return player;
                        });
    }
}
