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

public class RateLimiterFactory {

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
