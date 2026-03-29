package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.Config;
import fc.ul.scrimfinder.dto.response.player.PlayerDTO;
import fc.ul.scrimfinder.redis.RedisService;
import fc.ul.scrimfinder.service.PlayerFillingService;
import fc.ul.scrimfinder.service.RiotAdapterService;
import fc.ul.scrimfinder.util.ColoredMessage;
import fc.ul.scrimfinder.util.LogColor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class PlayerFillingServiceImpl implements PlayerFillingService {

    @Inject RiotAdapterService riotAdapterService;

    @Inject RedisService redisService;

    @Inject Config config;

    @Inject Logger logger;

    @Override
    public PlayerDTO getFilledPlayer(String name, String tag) {
        logger.info(
                ColoredMessage.withColor(
                        String.format("GET player from Riot with name %s and tag %s", name, tag),
                        LogColor.GREEN));
        String playerRedisKey = String.format("%s#%s", name, tag);
        return redisService
                .get(playerRedisKey, PlayerDTO.class)
                .orElseGet(
                        () -> {
                            PlayerDTO player = riotAdapterService.getPlayerData(name, tag);
                            redisService.set(playerRedisKey, player, config.redisCachePlayerKeyTtl());
                            return player;
                        });
    }
}
