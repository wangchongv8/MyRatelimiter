package wangchongv8.myratelimiter.algorithm;

import java.util.Arrays;
import java.util.List;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.redis.RedisOperations;

/**
 * 固定窗口（Fixed Window）限流算法。
 *
 * <p>将时间划分为固定窗口（如每秒），在每个窗口内维护一个计数器。
 * 简单高效，但窗口边界处可能出现流量尖峰（两个窗口交接时瞬间通过两倍流量）。
 *
 * <p>使用 Redis String 计数器 + TTL 自动过期实现。
 *
 * @see SlidingWindowLimiter 精度更高的滑动窗口替代方案
 */
public class FixedWindowLimiter extends AbstractRateLimiter {
    private static final String LUA_SCRIPT = loadScript("/lua/fixed_window.lua");

    public FixedWindowLimiter(RateLimiterConfig config, RedisOperations redisOps) {
        super(config, redisOps);
    }

    @Override
    public boolean tryAcquire(String key, int permits) {
        validate(key, permits);
        String redisKey = config.getRedisKeyPrefix() + key;
        List<String> args = Arrays.asList(
            String.valueOf(config.getIntervalSeconds()),
            String.valueOf(config.getPermits()),
            String.valueOf(permits)
        );
        Long result = redisOps.eval(LUA_SCRIPT, redisKey, args);
        return result != null && result == 1L;
    }
}
