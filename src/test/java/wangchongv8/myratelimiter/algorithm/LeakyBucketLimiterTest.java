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

public class LeakyBucketLimiterTest {

    private RedisOperations redisOps;
    private RateLimiter limiter;

    @Before
    public void setUp() {
        redisOps = mock(RedisOperations.class);
        RateLimiterConfig config = new RateLimiterConfig(10, 1, "rl:");
        limiter = new LeakyBucketLimiter(config, redisOps);
    }

    @Test
    public void shouldAllowRequestWithinCapacity() {
        when(redisOps.eval(anyString(), eq("rl:user:1"), anyList())).thenReturn(1L);
        assertTrue(limiter.tryAcquire("user:1"));
    }

    @Test
    public void shouldDenyWhenBucketFull() {
        when(redisOps.eval(anyString(), eq("rl:user:1"), anyList())).thenReturn(0L);
        assertFalse(limiter.tryAcquire("user:1"));
    }

    @Test
    public void shouldPassLeakRateInArgs() {
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        when(redisOps.eval(anyString(), eq("rl:user:1"), captor.capture())).thenReturn(1L);

        limiter.tryAcquire("user:1", 2);

        List<String> args = captor.getValue();
        assertEquals("10", args.get(0));   // capacity
        assertEquals("10.0", args.get(1)); // leak rate = permits/seconds
        assertEquals("2", args.get(2));    // requested
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNullKey() {
        limiter.tryAcquire(null);
    }
}
