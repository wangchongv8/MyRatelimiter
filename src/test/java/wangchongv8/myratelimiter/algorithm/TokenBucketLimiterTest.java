package wangchongv8.myratelimiter.algorithm;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.redis.RedisOperations;

public class TokenBucketLimiterTest {

    private RedisOperations redisOps;
    private RateLimiter limiter;

    @Before
    public void setUp() {
        redisOps = mock(RedisOperations.class);
        limiter = new TokenBucketLimiter(new RateLimiterConfig(1000, 1, "rl:"), redisOps);
    }

    @Test
    public void shouldAllowRequestWithinLimit() {
        when(redisOps.eval(anyString(), eq("rl:user:1"), anyList())).thenReturn(1L);
        assertTrue(limiter.tryAcquire("user:1"));
    }

    @Test
    public void shouldDenyRequestOverLimit() {
        when(redisOps.eval(anyString(), eq("rl:user:1"), anyList())).thenReturn(0L);
        assertFalse(limiter.tryAcquire("user:1"));
    }

    @Test
    public void shouldPassCorrectArgsToLua() {
        ArgumentCaptor<List<String>> argsCaptor = ArgumentCaptor.forClass(List.class);
        when(redisOps.eval(anyString(), eq("rl:user:1"), argsCaptor.capture())).thenReturn(1L);

        limiter.tryAcquire("user:1", 3);

        List<String> args = argsCaptor.getValue();
        assertEquals("1000", args.get(0));   // capacity
        assertEquals("1000.0", args.get(1)); // rate = 1000/1s
        assertEquals("3", args.get(2));      // requested permits
    }

    @Test
    public void shouldUseDefaultPermitsForTryAcquire() {
        ArgumentCaptor<List<String>> argsCaptor = ArgumentCaptor.forClass(List.class);
        when(redisOps.eval(anyString(), eq("rl:user:1"), argsCaptor.capture())).thenReturn(1L);

        limiter.tryAcquire("user:1");

        List<String> args = argsCaptor.getValue();
        assertEquals("1", args.get(2));   // default 1 permit
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

        try {
            limiter.acquire("user:1", 1, 200, java.util.concurrent.TimeUnit.MILLISECONDS);
            fail("expected RateLimitExceededException");
        } catch (wangchongv8.myratelimiter.core.RateLimitExceededException e) {
            // 预期在超时前抛出异常
        }
    }
}
