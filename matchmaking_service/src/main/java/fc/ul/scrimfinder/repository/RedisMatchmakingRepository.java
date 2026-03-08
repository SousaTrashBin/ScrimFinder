package fc.ul.scrimfinder.repository;

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

    private String getQueueKey(Long queueId) {
        return "matchmaking:queue:" + queueId + ":tickets";
    }

    private String getRoleQueueKey(Long queueId, Role role) {
        return "matchmaking:queue:" + queueId + ":role:" + role.name() + ":tickets";
    }

    public void addTicket(Long queueId, Role role, Long ticketId, int mmr) {
        String key = (role == Role.NONE) ? getQueueKey(queueId) : getRoleQueueKey(queueId, role);
        zset.zadd(key, mmr, ticketId);
    }

    public boolean removeTicket(Long queueId, Role role, Long ticketId) {
        String key = (role == Role.NONE) ? getQueueKey(queueId) : getRoleQueueKey(queueId, role);
        long removed = zset.zrem(key, ticketId);
        return removed > 0;
    }

    public List<Long> getTickets(Long queueId, Role role) {
        String key = (role == Role.NONE) ? getQueueKey(queueId) : getRoleQueueKey(queueId, role);
        return zset.zrange(key, 0, -1);
    }

    public List<Long> getTicketsInMMRRange(Long queueId, Role role, int minMMR, int maxMMR) {
        String key = (role == Role.NONE) ? getQueueKey(queueId) : getRoleQueueKey(queueId, role);
        return zset.zrangebyscore(key,
                io.quarkus.redis.datasource.sortedset.ScoreRange.from(minMMR, maxMMR));
    }
}
