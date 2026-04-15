package fc.ul.scrimfinder.repository;

import fc.ul.scrimfinder.util.Region;
import fc.ul.scrimfinder.util.Role;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.sortedset.SortedSetCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class RedisMatchmakingRepository {

    private final SortedSetCommands<String, UUID> zset;

    @Inject
    public RedisMatchmakingRepository(RedisDataSource ds) {
        this.zset = ds.sortedSet(UUID.class);
    }

    private String getQueueKey(UUID queueId, Region region) {
        return "matchmaking:queue:" + queueId.toString() + ":region:" + region.name() + ":tickets";
    }

    private String getRoleQueueKey(UUID queueId, Role role, Region region) {
        return "matchmaking:queue:"
                + queueId.toString()
                + ":region:"
                + region.name()
                + ":role:"
                + role.name()
                + ":tickets";
    }

    public void addTicket(UUID queueId, Region region, Role role, UUID ticketId, int mmr) {
        String key =
                (role == Role.NONE) ? getQueueKey(queueId, region) : getRoleQueueKey(queueId, role, region);
        zset.zadd(key, mmr, ticketId);
    }

    public boolean removeTicket(UUID queueId, Region region, Role role, UUID ticketId) {
        String key =
                (role == Role.NONE) ? getQueueKey(queueId, region) : getRoleQueueKey(queueId, role, region);
        long removed = zset.zrem(key, ticketId);
        return removed > 0;
    }

    public List<UUID> getTickets(UUID queueId, Region region, Role role) {
        String key =
                (role == Role.NONE) ? getQueueKey(queueId, region) : getRoleQueueKey(queueId, role, region);
        return zset.zrange(key, 0, -1);
    }

    public List<UUID> getTicketsInMMRRange(
            UUID queueId, Region region, Role role, int minMMR, int maxMMR) {
        String key =
                (role == Role.NONE) ? getQueueKey(queueId, region) : getRoleQueueKey(queueId, role, region);
        return zset.zrangebyscore(
                key, io.quarkus.redis.datasource.sortedset.ScoreRange.from(minMMR, maxMMR));
    }
}
