package wangchongv8.myratelimiter.algorithm;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.core.RateLimitExceededException;
import wangchongv8.myratelimiter.redis.RedisOperations;

public abstract class AbstractRateLimiter implements RateLimiter {
    protected final RateLimiterConfig config;
    protected final RedisOperations redisOps;

    protected AbstractRateLimiter(RateLimiterConfig config, RedisOperations redisOps) {
        this.config = config;
        this.redisOps = redisOps;
    }

    protected static String loadScript(String path) {
        try (InputStream is = AbstractRateLimiter.class.getResourceAsStream(path);
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

    protected void validate(String key, int permits) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be > 0");
        }
    }

    @Override
    public boolean tryAcquire(String key) {
        return tryAcquire(key, 1);
    }

    @Override
    public void acquire(String key) {
        acquire(key, 1, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    @Override
    public void acquire(String key, int permits) {
        acquire(key, permits, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    @Override
    public void acquire(String key, int permits, long timeout, TimeUnit unit) {
        validate(key, permits);
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (true) {
            if (tryAcquire(key, permits)) {
                return;
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new RateLimitExceededException(
                    "Rate limit exceeded for key: " + key);
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RateLimitExceededException("Interrupted while waiting for rate limit");
            }
        }
    }
}
