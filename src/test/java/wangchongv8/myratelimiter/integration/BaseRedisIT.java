package wangchongv8.myratelimiter.integration;

import com.github.fppt.jedismock.RedisServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import redis.clients.jedis.JedisPool;

/**
 * Abstract base for integration tests using embedded Redis mock.
 *
 * <p>Starts an in-process fake Redis server on port 16379 before any tests run,
 * and stops it after all tests complete. Subclasses use {@link #jedisPool}
 * to create {@code JedisRedisOps}.
 */
public abstract class BaseRedisIT {

    protected static RedisServer redisServer;
    protected static JedisPool jedisPool;

    @BeforeClass
    public static void startRedis() throws Exception {
        redisServer = new RedisServer(16379);
        redisServer.start();
        jedisPool = new JedisPool("localhost", 16379);
    }

    @AfterClass
    public static void stopRedis() {
        if (jedisPool != null) {
            jedisPool.close();
        }
        if (redisServer != null) {
            try {
                redisServer.stop();
            } catch (Exception ignored) {
            }
        }
    }
}
