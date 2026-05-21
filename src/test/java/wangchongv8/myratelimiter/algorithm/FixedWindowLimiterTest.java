package wangchongv8.myratelimiter.algorithm;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.redis.RedisOperations;

public class FixedWindowLimiterTest {

    private RedisOperations redisOps;
    private RateLimiter limiter;

    @Before
    public void setUp() {
        redisOps = mock(RedisOperations.class);
        RateLimiterConfig config = new RateLimiterConfig(5, 60, "rl:");
        limiter = new FixedWindowLimiter(config, redisOps);
    }

    @Test
    public void shouldAllowRequestWithinWindow() {
        when(redisOps.eval(anyString(), eq("rl:user:1"), anyList())).thenReturn(1L);
        assertTrue(limiter.tryAcquire("user:1"));
    }

    @Test
    public void shouldDenyWhenWindowExhausted() {
        when(redisOps.eval(anyString(), eq("rl:user:1"), anyList())).thenReturn(0L);
        assertFalse(limiter.tryAcquire("user:1"));
    }

    @Test
    public void shouldPassWindowSizeInArgs() {
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        when(redisOps.eval(anyString(), eq("rl:user:1"), captor.capture())).thenReturn(1L);

        limiter.tryAcquire("user:1", 2);

        List<String> args = captor.getValue();
        assertEquals("60", args.get(0));  // 60 second window
        assertEquals("5", args.get(1));   // max 5 per window
        assertEquals("2", args.get(2));   // requested permits
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNullKey() {
        limiter.tryAcquire(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectZeroPermits() {
        limiter.tryAcquire("user:1", 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNegativePermits() {
        limiter.tryAcquire("user:1", -1);
    }

    @Test
    public void shouldPropagateRedisException() {
        when(redisOps.eval(anyString(), anyString(), anyList()))
            .thenThrow(new RuntimeException("redis down"));
        try {
            limiter.tryAcquire("user:1");
            fail("expected exception");
        } catch (RuntimeException e) {
            assertEquals("redis down", e.getMessage());
        }
    }

    @Test
    public void shouldAcquireBlockUntilAllowed() {
        when(redisOps.eval(anyString(), eq("rl:user:1"), anyList()))
            .thenReturn(0L, 1L);
        limiter.acquire("user:1");
        verify(redisOps, times(2)).eval(anyString(), eq("rl:user:1"), anyList());
    }

    @Test
    public void shouldAcquireThrowOnTimeout() {
        when(redisOps.eval(anyString(), eq("rl:user:1"), anyList())).thenReturn(0L);
        long start = System.currentTimeMillis();
        try {
            limiter.acquire("user:1", 1, 200, java.util.concurrent.TimeUnit.MILLISECONDS);
            fail("expected RateLimitExceededException");
        } catch (wangchongv8.myratelimiter.core.RateLimitExceededException e) {
            long elapsed = System.currentTimeMillis() - start;
            assertTrue(elapsed >= 200);
        }
    }
}
