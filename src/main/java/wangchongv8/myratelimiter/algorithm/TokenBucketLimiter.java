package wangchongv8.myratelimiter.algorithm;

import java.util.Arrays;
import java.util.List;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.redis.RedisOperations;

/**
 * 令牌桶（Token Bucket）限流算法。
 *
 * <p>以恒定速率往桶中放入令牌，桶有容量上限。请求消耗令牌，令牌不足时拒绝。
 * 适用于需要容忍流量突发（积攒令牌）的场景。
 *
 * <p>使用 Redis Hash 存储桶状态 {@code {tokens, last_refill_time}}，
 * Lua 脚本保证原子性。
 *
 * <pre>{@code
 * RateLimiter limiter = new TokenBucketLimiter(
 *     new RateLimiterConfig(100, 1, "rl:"),
 *     new JedisRedisOps(jedisPool)
 * );
 * limiter.tryAcquire("user:123"); // true/false
 * }</pre>
 */
public class TokenBucketLimiter extends AbstractRateLimiter {
    /** 预编译的 Lua 脚本 */
    private static final String LUA_SCRIPT = loadScript("/lua/token_bucket.lua");
    /** 令牌填充速率 = permits / intervalSeconds */
    private final double rate;

    public TokenBucketLimiter(RateLimiterConfig config, RedisOperations redisOps) {
        super(config, redisOps);
        this.rate = (double) config.getPermits() / config.getIntervalSeconds();
    }

    @Override
    public boolean tryAcquire(String key, int permits) {
        validate(key, permits);
        String redisKey = config.getRedisKeyPrefix() + key;
        List<String> args = Arrays.asList(
            String.valueOf(config.getPermits()),
            String.valueOf(rate),
            String.valueOf(permits),
            String.valueOf(System.currentTimeMillis())
        );
        Long result = redisOps.eval(LUA_SCRIPT, redisKey, args);
        return result != null && result == 1L;
    }
}
