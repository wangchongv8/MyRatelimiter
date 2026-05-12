package wangchongv8.myratelimiter.core;

import java.util.Objects;

public class RateLimiterConfig {
    private final int permits;
    private final int intervalSeconds;
    private final String redisKeyPrefix;

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

    public int getPermits() { return permits; }
    public int getIntervalSeconds() { return intervalSeconds; }
    public String getRedisKeyPrefix() { return redisKeyPrefix; }
}
