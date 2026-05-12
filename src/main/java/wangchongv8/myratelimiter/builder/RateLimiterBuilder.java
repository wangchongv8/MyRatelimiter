package wangchongv8.myratelimiter.builder;

import wangchongv8.myratelimiter.core.Algorithm;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.redis.RedisOperations;

/**
 * 限流器链式构建器，用户主入口。
 *
 * <p>使用方式：
 * <pre>{@code
 * RateLimiter limiter = new RateLimiterBuilder()
 *     .algorithm(Algorithm.TOKEN_BUCKET)
 *     .permits(100)
 *     .perSecond(1)
 *     .redisOps(new JedisRedisOps(jedisPool))
 *     .build();
 * }</pre>
 *
 * <p>必填项：{@code algorithm}、{@code permits}、{@code redisOps}。
 * 默认值：时间窗口 1 秒，key 前缀 "rl:"。
 *
 * @see RateLimiterFactory 直接构造方式
 */
public class RateLimiterBuilder {
    private Algorithm algorithm;
    private int permits;
    /** 时间窗口（秒），默认 1 */
    private int intervalSeconds = 1;
    /** Redis key 前缀，默认 "rl:" */
    private String redisKeyPrefix = "rl:";
    private RedisOperations redisOps;

    /** 设置限流算法（必填） */
    public RateLimiterBuilder algorithm(Algorithm algorithm) {
        this.algorithm = algorithm;
        return this;
    }

    /** 设置速率上限（必填，必须大于 0） */
    public RateLimiterBuilder permits(int permits) {
        this.permits = permits;
        return this;
    }

    /** 设置时间窗口（秒），默认 1 秒。如 {@code .perSecond(2)} 表示每 2 秒为一个窗口 */
    public RateLimiterBuilder perSecond(int seconds) {
        this.intervalSeconds = seconds;
        return this;
    }

    /** 设置时间窗口（分钟），将自动转换为秒。{@code .perMinute(1)} 等价于 {@code .perSecond(60)} */
    public RateLimiterBuilder perMinute(int minutes) {
        this.intervalSeconds = minutes * 60;
        return this;
    }

    /** 设置 Redis key 前缀，默认 "rl:" */
    public RateLimiterBuilder redisKeyPrefix(String prefix) {
        this.redisKeyPrefix = prefix;
        return this;
    }

    /** 设置 Redis 操作实现（必填），可选择 {@link JedisRedisOps} 或 {@link RedissonRedisOps} */
    public RateLimiterBuilder redisOps(RedisOperations redisOps) {
        this.redisOps = redisOps;
        return this;
    }

    /**
     * 校验参数并构建限流器实例。
     *
     * @return 对应算法的 {@link RateLimiter} 实例
     * @throws IllegalArgumentException 缺少必填参数或参数不合法
     */
    public RateLimiter build() {
        if (algorithm == null) throw new IllegalArgumentException("algorithm is required");
        if (permits <= 0) throw new IllegalArgumentException("permits must be > 0, got: " + permits);
        if (redisOps == null) throw new IllegalArgumentException("redisOps is required");

        RateLimiterConfig config = new RateLimiterConfig(permits, intervalSeconds, redisKeyPrefix);
        return RateLimiterFactory.create(algorithm, config, redisOps);
    }
}
