package wangchongv8.myratelimiter.builder;

import wangchongv8.myratelimiter.core.Algorithm;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.redis.RedisOperations;

public class RateLimiterBuilder {
    private Algorithm algorithm;
    private int permits;
    private int intervalSeconds = 1;
    private String redisKeyPrefix = "rl:";
    private RedisOperations redisOps;

    public RateLimiterBuilder algorithm(Algorithm algorithm) {
        this.algorithm = algorithm;
        return this;
    }

    public RateLimiterBuilder permits(int permits) {
        this.permits = permits;
        return this;
    }

    public RateLimiterBuilder perSecond(int seconds) {
        this.intervalSeconds = seconds;
        return this;
    }

    public RateLimiterBuilder perMinute(int minutes) {
        this.intervalSeconds = minutes * 60;
        return this;
    }

    public RateLimiterBuilder redisKeyPrefix(String prefix) {
        this.redisKeyPrefix = prefix;
        return this;
    }

    public RateLimiterBuilder redisOps(RedisOperations redisOps) {
        this.redisOps = redisOps;
        return this;
    }

    public RateLimiter build() {
        if (algorithm == null) throw new IllegalArgumentException("algorithm is required");
        if (permits <= 0) throw new IllegalArgumentException("permits must be > 0, got: " + permits);
        if (redisOps == null) throw new IllegalArgumentException("redisOps is required");

        RateLimiterConfig config = new RateLimiterConfig(permits, intervalSeconds, redisKeyPrefix);
        return RateLimiterFactory.create(algorithm, config, redisOps);
    }
}
