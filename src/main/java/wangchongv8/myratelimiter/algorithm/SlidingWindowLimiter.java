package wangchongv8.myratelimiter.algorithm;

import java.util.Arrays;
import java.util.List;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.redis.RedisOperations;

/**
 * 滑动窗口（Sliding Window）限流算法。
 *
 * <p>基于 Redis 有序集合（ZSET）实现，每个请求的时间戳作为 score。
 * 计算时移除窗口外的旧记录，统计窗口内记录数判断是否放行。
 * 相比固定窗口精度更高，但开销也更大（ZSET 内存占用）。
 */
public class SlidingWindowLimiter extends AbstractRateLimiter {
    private static final String LUA_SCRIPT = loadScript("/lua/sliding_window.lua");

    public SlidingWindowLimiter(RateLimiterConfig config, RedisOperations redisOps) {
        super(config, redisOps);
    }

    @Override
    public boolean tryAcquire(String key, int permits) {
        validate(key, permits);
        long now = System.currentTimeMillis();
        String redisKey = config.getRedisKeyPrefix() + key;
        List<String> args = Arrays.asList(
            String.valueOf(config.getIntervalSeconds() * 1000L),
            String.valueOf(config.getPermits()),
            String.valueOf(now),
            String.valueOf(System.nanoTime()),
            String.valueOf(permits)
        );
        Long result = redisOps.eval(LUA_SCRIPT, redisKey, args);
        return result != null && result == 1L;
    }
}
