package wangchongv8.myratelimiter.algorithm;

import java.util.Arrays;
import java.util.List;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.redis.RedisOperations;

/**
 * 滑动窗口（Sliding Window）限流算法。
 *
 * <p>基于 Redis Hash 子窗口计数器实现：将窗口切分为 ~10 个子窗口，
 * 每个子窗口独立计数。查询时惰性删除过期子窗口，统计有效子窗口
 * 计数之和判断是否放行。相比固定窗口无边界尖峰问题，
 * 相比 SlidingLog 内存有界（O(子窗口数) 而非 O(请求数)）。
 */
public class SlidingWindowLimiter extends AbstractRateLimiter {
    private static final String LUA_SCRIPT = loadScript("/lua/sliding_window.lua");

    public SlidingWindowLimiter(RateLimiterConfig config, RedisOperations redisOps) {
        super(config, redisOps);
    }

    @Override
    public boolean tryAcquire(String key, int permits) {
        validate(key, permits);
        String redisKey = config.getRedisKeyPrefix() + key;
        List<String> args = Arrays.asList(
            String.valueOf(config.getIntervalSeconds() * 1000L),
            String.valueOf(config.getPermits()),
            String.valueOf(permits)
        );
        Long result = redisOps.eval(LUA_SCRIPT, redisKey, args);
        return result != null && result == 1L;
    }
}
