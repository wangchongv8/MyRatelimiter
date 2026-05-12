package wangchongv8.myratelimiter.redis;

import java.util.Collections;
import java.util.List;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;

/**
 * 基于 Redisson 客户端的 {@link RedisOperations} 实现。
 *
 * <p>Redisson 的 {@link RScript} 内部自动处理 evalsha/eval 降级，
 * 无需手动管理脚本缓存。
 *
 * <pre>{@code
 * Config config = new Config();
 * config.useSingleServer().setAddress("redis://localhost:6379");
 * RedissonClient client = Redisson.create(config);
 * RedisOperations ops = new RedissonRedisOps(client);
 * }</pre>
 */
public class RedissonRedisOps implements RedisOperations {
    private final RedissonClient client;

    /**
     * @param client Redisson 客户端实例
     */
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
