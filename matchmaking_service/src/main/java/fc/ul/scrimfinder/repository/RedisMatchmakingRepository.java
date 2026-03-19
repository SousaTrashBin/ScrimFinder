package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.util.Region;
import fc.ul.scrimfinder.util.Role;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.sortedset.SortedSetCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class RedisMatchmakingRepository {

    private final SortedSetCommands<String, Long> zset;

    @Inject
    public RedisMatchmakingRepository(RedisDataSource ds) {
        this.zset = ds.sortedSet(Long.class);
    }

    private String getQueueKey(Long queueId, Region region) {
        return "matchmaking:queue:" + queueId + ":region:" + region.name() + ":tickets";
    }

    private String getRoleQueueKey(Long queueId, Role role, Region region) {
        return "matchmaking:queue:"
                + queueId
                + ":region:"
                + region.name()
                + ":role:"
                + role.name()
                + ":tickets";
    }

    public void addTicket(Long queueId, Region region, Role role, Long ticketId, int mmr) {
        String key =
                (role == Role.NONE) ? getQueueKey(queueId, region) : getRoleQueueKey(queueId, role, region);
        zset.zadd(key, mmr, ticketId);
    }

    public boolean removeTicket(Long queueId, Region region, Role role, Long ticketId) {
        String key =
                (role == Role.NONE) ? getQueueKey(queueId, region) : getRoleQueueKey(queueId, role, region);
        long removed = zset.zrem(key, ticketId);
        return removed > 0;
    }

    public List<Long> getTickets(Long queueId, Region region, Role role) {
        String key =
                (role == Role.NONE) ? getQueueKey(queueId, region) : getRoleQueueKey(queueId, role, region);
        return zset.zrange(key, 0, -1);
    }

    public List<Long> getTicketsInMMRRange(
            Long queueId, Region region, Role role, int minMMR, int maxMMR) {
        String key =
                (role == Role.NONE) ? getQueueKey(queueId, region) : getRoleQueueKey(queueId, role, region);
        return zset.zrangebyscore(
                key, io.quarkus.redis.datasource.sortedset.ScoreRange.from(minMMR, maxMMR));
    }
}
