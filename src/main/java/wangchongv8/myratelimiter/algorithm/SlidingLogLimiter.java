package wangchongv8.myratelimiter.algorithm;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.redis.RedisOperations;

/**
 * 滑动日志（Sliding Log）限流算法。
 *
 * <p>基于 Redis ZSET 实现，每次请求作为独立条目记录，时间戳为 score。
 * 精度最高（逐请求精确判断），保留完整请求日志可审计回溯。
 * 内存随请求数增长，高流量下开销大。
 *
 * @see SlidingWindowLimiter 内存有界的子窗口计数器替代方案
 */
public class SlidingLogLimiter extends AbstractRateLimiter {
    private static final String LUA_SCRIPT = loadScript("/lua/sliding_log.lua");
    /** 实例唯一前缀，防止分布式环境下不同 JVM 生成相同的 ZSET member */
    private static final String INSTANCE_ID =
        UUID.randomUUID().toString().substring(0, 8);

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
            INSTANCE_ID + ":" + System.nanoTime(),
            String.valueOf(permits)
        );
        Long result = redisOps.eval(LUA_SCRIPT, redisKey, args);
        return result != null && result == 1L;
    }
}
