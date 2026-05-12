package wangchongv8.myratelimiter.core;

import java.util.Objects;

/**
 * 限流器配置，不可变。
 *
 * <p>指定速率上限、时间窗口大小以及 Redis key 前缀。
 * 可用于多个限流器实例共享同一配置。
 *
 * <pre>{@code
 * RateLimiterConfig config = new RateLimiterConfig(100, 1, "rl:");
 * }</pre>
 */
public class RateLimiterConfig {
    private final int permits;
    private final int intervalSeconds;
    private final String redisKeyPrefix;

    /**
     * 创建限流配置。
     *
     * @param permits         速率上限，必须大于 0
     * @param intervalSeconds 时间窗口（秒），必须大于 0
     * @param redisKeyPrefix  Redis key 前缀，不能为 null
     */
    public RateLimiterConfig(int permits, int intervalSeconds, String redisKeyPrefix) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be > 0");
        }
        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException("intervalSeconds must be > 0");
        }
        Objects.requireNonNull(redisKeyPrefix, "redisKeyPrefix must not be null");
        this.permits = permits;
        this.intervalSeconds = intervalSeconds;
        this.redisKeyPrefix = redisKeyPrefix;
    }

    /** @return 速率上限 */
    public int getPermits() { return permits; }

    /** @return 时间窗口（秒） */
    public int getIntervalSeconds() { return intervalSeconds; }

    /** @return Redis key 前缀，如 "rl:" */
    public String getRedisKeyPrefix() { return redisKeyPrefix; }
}
