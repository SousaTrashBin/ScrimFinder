package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.Config;
import fc.ul.scrimfinder.dto.response.match.MatchStatsDTO;
import fc.ul.scrimfinder.redis.RedisService;
import fc.ul.scrimfinder.service.MatchFillingService;
import fc.ul.scrimfinder.service.RiotAdapterService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MatchFillingServiceImpl implements MatchFillingService {

    @Inject RiotAdapterService riotAdapterService;

    @Inject RedisService redisService;

    @Inject Config config;

    @Override
    public MatchStatsDTO getFilledMatch(String matchId) {
        return redisService
                .get(matchId, MatchStatsDTO.class)
                .orElseGet(
                        () -> {
                            MatchStatsDTO match = riotAdapterService.getMatchData(matchId);
                            redisService.set(matchId, match, config.redisCacheMatchKeyTtl());
                            return match;
                        });
    }

    @Override
    public String getRawMatchData(String matchId) {
        return redisService
                .get(matchId + "raw", String.class)
                .orElseGet(
                        () -> {
                            String match = riotAdapterService.getRawMatchData(matchId);
                            redisService.set(matchId + "raw", match, config.redisCacheMatchKeyTtl());
                            return match;
                        });
    }
}
