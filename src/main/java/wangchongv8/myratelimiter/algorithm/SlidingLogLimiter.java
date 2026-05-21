package wangchongv8.myratelimiter.algorithm;

import java.util.Arrays;
import java.util.List;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.redis.RedisOperations;

/**
 * 滑动日志（Sliding Log）限流算法。
 *
 * <p>与滑动窗口实现相同（ZSET），语义上记录每次请求的精确时间戳。
 * 精度最高但 Redis 内存开销最大，适用于需要精确审计的场景。
 *
 * @see SlidingWindowLimiter 内存开销更小的滑动窗口替代方案
 */
public class SlidingLogLimiter extends AbstractRateLimiter {
    private static final String LUA_SCRIPT = loadScript("/lua/sliding_log.lua");

    public SlidingLogLimiter(RateLimiterConfig config, RedisOperations redisOps) {
        super(config, redisOps);
    }

    @Override
    public boolean tryAcquire(String key, int permits) {
        validate(key, permits);
        String redisKey = config.getRedisKeyPrefix() + key;
        List<String> args = Arrays.asList(
            String.valueOf(config.getIntervalSeconds() * 1000L),
            String.valueOf(config.getPermits()),
            String.valueOf(System.nanoTime()),
            String.valueOf(permits)
        );
        Long result = redisOps.eval(LUA_SCRIPT, redisKey, args);
        return result != null && result == 1L;
    }
}
