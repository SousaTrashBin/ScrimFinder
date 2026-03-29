package fc.ul.scrimfinder.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fc.ul.scrimfinder.util.ColoredMessage;
import fc.ul.scrimfinder.util.LogColor;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.jboss.logging.Logger;

@ApplicationScoped
public class RedisService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    @Inject Logger logger;
    private KeyCommands<String> keyCommands;
    private ValueCommands<String, String> valueCommands;

    public RedisService(RedisDataSource redisDataSource) {
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        keyCommands = redisDataSource.key(String.class);
        valueCommands = redisDataSource.value(String.class);
    }

    public <T> Optional<T> get(String key, Class<T> returnType) {
        String result = valueCommands.get(key);
        if (result == null) {
            return Optional.empty();
        }
        try {
            logger.info(ColoredMessage.withColor("Lookup key in Redis cache: " + key, LogColor.GREEN));
            return Optional.ofNullable(MAPPER.readValue(result, returnType));
        } catch (Exception x) {
            logger.warn(
                    ColoredMessage.withColor(
                            "Unable to lookup value in Redis cache due to unexpected format. Value: " + result,
                            LogColor.YELLOW));
        }
        return Optional.empty();
    }

    public <T> void set(String key, T value, Long ttlInSeconds) {
        try {
            logger.info(
                    ColoredMessage.withColor(
                            String.format("Store key %s for %d seconds in Redis service", key, ttlInSeconds),
                            LogColor.GREEN));
            valueCommands.setex(key, ttlInSeconds, MAPPER.writeValueAsString(value));
        } catch (Exception x) {
            logger.warn(
                    ColoredMessage.withColor(
                            "Unable to cache value due to unexpected format. Key: " + key, LogColor.YELLOW));
        }
    }

    public List<String> keys(String pattern) {
        return keyCommands.keys(pattern);
    }
}
