package wangchongv8.myratelimiter.algorithm;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.core.RateLimitExceededException;
import wangchongv8.myratelimiter.redis.RedisOperations;

public class LeakyBucketLimiter implements RateLimiter {
    private static final String LUA_SCRIPT = loadScript("/lua/leaky_bucket.lua");

    private final RateLimiterConfig config;
    private final RedisOperations redisOps;
    private final double rate;

    public LeakyBucketLimiter(RateLimiterConfig config, RedisOperations redisOps) {
        this.config = config;
        this.redisOps = redisOps;
        this.rate = (double) config.getPermits() / config.getIntervalSeconds();
    }

    @Override
    public boolean tryAcquire(String key) { return tryAcquire(key, 1); }

    @Override
    public boolean tryAcquire(String key, int permits) {
        if (key == null) throw new IllegalArgumentException("key must not be null");
        if (permits <= 0) throw new IllegalArgumentException("permits must be > 0");
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

    @Override
    public void acquire(String key) { tryAcquire(key); }

    @Override
    public void acquire(String key, int permits) { tryAcquire(key, permits); }

    @Override
    public void acquire(String key, int permits, long timeout, TimeUnit unit) {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (!tryAcquire(key, permits)) {
            if (System.currentTimeMillis() >= deadline) {
                throw new RateLimitExceededException("Rate limit exceeded for key: " + key);
            }
            try { Thread.sleep(10); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RateLimitExceededException("Interrupted");
            }
        }
    }

    private static String loadScript(String path) {
        try (InputStream is = LeakyBucketLimiter.class.getResourceAsStream(path);
             BufferedReader reader = new BufferedReader(
                 new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Lua script: " + path, e);
        }
    }
}
