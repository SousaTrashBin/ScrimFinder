package fc.ul.scrimfinder.util;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;

@ApplicationScoped
public class DistributedLockService {

    private final ValueCommands<String, String> valueCommands;
    private final KeyCommands<String> keyCommands;

    @Inject
    public DistributedLockService(RedisDataSource ds) {
        this.valueCommands = ds.value(String.class);
        this.keyCommands = ds.key();
    }

    public boolean acquireLock(String lockKey, Duration timeout) {
        return valueCommands.setnx(lockKey, "locked")
                && keyCommands.pexpire(lockKey, timeout.toMillis());
    }

    public void releaseLock(String lockKey) {
        keyCommands.del(lockKey);
    }
}
