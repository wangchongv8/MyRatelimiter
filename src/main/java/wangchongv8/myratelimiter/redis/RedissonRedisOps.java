package wangchongv8.myratelimiter.redis;

import java.util.Collections;
import java.util.List;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;

public class RedissonRedisOps implements RedisOperations {
    private final RedissonClient client;

    public RedissonRedisOps(RedissonClient client) {
        this.client = client;
    }

    @Override
    public Long eval(String script, String key, List<String> args) {
        return client.getScript().eval(
            RScript.Mode.READ_WRITE,
            script,
            RScript.ReturnType.INTEGER,
            Collections.singletonList(key),
            args.toArray()
        );
    }
}
