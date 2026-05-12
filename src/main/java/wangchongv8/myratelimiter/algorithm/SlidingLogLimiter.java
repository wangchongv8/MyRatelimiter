package wangchongv8.myratelimiter.algorithm;

import java.util.Arrays;
import java.util.List;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.redis.RedisOperations;

public class SlidingLogLimiter extends AbstractRateLimiter {
    private static final String LUA_SCRIPT = loadScript("/lua/sliding_log.lua");

    public SlidingLogLimiter(RateLimiterConfig config, RedisOperations redisOps) {
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
