package wangchongv8.myratelimiter.algorithm;

import java.util.Arrays;
import java.util.List;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.redis.RedisOperations;

/**
 * 漏桶（Leaky Bucket）限流算法。
 *
 * <p>请求进入桶中，桶以恒定速率漏水。桶满时新请求被拒绝。
 * 与令牌桶不同，漏桶**不**容忍突发流量——即使桶有剩余空间，出水速率也是恒定的。
 *
 * <p>使用 Redis Hash 存储桶状态 {@code {water, last_leak_time}}，
 * water 表示当前水位，每次请求先计算漏水量再判断是否溢出。
 */
public class LeakyBucketLimiter extends AbstractRateLimiter {
    /** 预编译的 Lua 脚本 */
    private static final String LUA_SCRIPT = loadScript("/lua/leaky_bucket.lua");
    /** 漏水速率 = permits / intervalSeconds */
    private final double rate;

    public LeakyBucketLimiter(RateLimiterConfig config, RedisOperations redisOps) {
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
