package fc.ul.scrimfinder.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fc.ul.scrimfinder.Config;
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
    @Inject Config config;
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
        try {
            return Optional.ofNullable(MAPPER.readValue(result, returnType));
        } catch (Exception x) {
            logger.warn("Unable to lookup value in cache due to unexpected format. Value: " + result);
        }
        return Optional.empty();
    }

    public <T> void set(String key, T value) {
        try {
            valueCommands.setex(key, config.redisCacheKeyTtl(), MAPPER.writeValueAsString(value));
        } catch (Exception x) {
            logger.warn("Unable to cache value due to unexpected format. Key: " + key);
        }
    }

    public List<String> keys(String pattern) {
        return keyCommands.keys(pattern);
    }
}
