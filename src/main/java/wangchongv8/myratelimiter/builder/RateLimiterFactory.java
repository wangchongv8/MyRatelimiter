package wangchongv8.myratelimiter.builder;

import wangchongv8.myratelimiter.algorithm.FixedWindowLimiter;
import wangchongv8.myratelimiter.algorithm.LeakyBucketLimiter;
import wangchongv8.myratelimiter.algorithm.SlidingLogLimiter;
import wangchongv8.myratelimiter.algorithm.SlidingWindowLimiter;
import wangchongv8.myratelimiter.algorithm.TokenBucketLimiter;
import wangchongv8.myratelimiter.core.Algorithm;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.redis.RedisOperations;

/**
 * 限流器工厂，根据算法枚举创建对应的实现类。
 *
 * <p>等效于 {@link RateLimiterBuilder} 的底层 API，适用于需要直接构造的场景：
 * <pre>{@code
 * RateLimiterConfig config = new RateLimiterConfig(100, 1, "rl:");
 * RateLimiter limiter = RateLimiterFactory.create(
 *     Algorithm.SLIDING_WINDOW, config, new JedisRedisOps(jedisPool));
 * }</pre>
 *
 * @see RateLimiterBuilder 推荐使用的链式构建方式
 */
public class RateLimiterFactory {

    /**
     * 创建限流器实例。
     *
     * @param algorithm 限流算法枚举，不能为 null
     * @param config    限流配置，不能为 null
     * @param redisOps  Redis 操作实现，不能为 null
     * @return 对应算法的 {@link RateLimiter} 实例
     * @throws IllegalArgumentException 任一参数为 null
     */
    public static RateLimiter create(Algorithm algorithm, RateLimiterConfig config, RedisOperations redisOps) {
        if (algorithm == null) throw new IllegalArgumentException("algorithm must not be null");
        if (config == null) throw new IllegalArgumentException("config must not be null");
        if (redisOps == null) throw new IllegalArgumentException("redisOps must not be null");

        switch (algorithm) {
            case TOKEN_BUCKET:
                return new TokenBucketLimiter(config, redisOps);
            case LEAKY_BUCKET:
                return new LeakyBucketLimiter(config, redisOps);
            case FIXED_WINDOW:
                return new FixedWindowLimiter(config, redisOps);
            case SLIDING_WINDOW:
                return new SlidingWindowLimiter(config, redisOps);
            case SLIDING_LOG:
                return new SlidingLogLimiter(config, redisOps);
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        }
    }
}
