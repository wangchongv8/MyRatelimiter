package wangchongv8.myratelimiter.algorithm;

import java.util.Arrays;
import java.util.List;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.redis.RedisOperations;

public class TokenBucketLimiter extends AbstractRateLimiter {
    private static final String LUA_SCRIPT = loadScript("/lua/token_bucket.lua");
    private final double rate;

    public TokenBucketLimiter(RateLimiterConfig config, RedisOperations redisOps) {
        super(config, redisOps);
        this.rate = (double) config.getPermits() / config.getIntervalSeconds();
    }

    @Override
    public boolean tryAcquire(String key, int permits) {
        validate(key, permits);
        String redisKey = config.getRedisKeyPrefix() + key;
        List<String> args = Arrays.asList(
            String.valueOf(config.getPermits()),
            String.valueOf(rate),
            String.valueOf(permits),
            String.valueOf(System.currentTimeMillis())
        );
        Long result = redisOps.eval(LUA_SCRIPT, redisKey, args);
        return result != null && result == 1L;
    }
}
