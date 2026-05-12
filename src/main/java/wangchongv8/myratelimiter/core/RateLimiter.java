package wangchongv8.myratelimiter.core;

import java.util.concurrent.TimeUnit;

public interface RateLimiter {
    boolean tryAcquire(String key);
    boolean tryAcquire(String key, int permits);
    void acquire(String key) throws RateLimitExceededException;
    void acquire(String key, int permits) throws RateLimitExceededException;
    void acquire(String key, int permits, long timeout, TimeUnit unit) throws RateLimitExceededException;
}
