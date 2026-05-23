package wangchongv8.myratelimiter.integration;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import wangchongv8.myratelimiter.core.Algorithm;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.builder.RateLimiterBuilder;
import wangchongv8.myratelimiter.redis.JedisRedisOps;

public class FixedWindowIT extends BaseRedisIT {

    private RateLimiter limiter;

    @Before
    public void setUp() {
        limiter = new RateLimiterBuilder()
            .algorithm(Algorithm.FIXED_WINDOW)
            .permits(5)
            .perSecond(1)
            .redisOps(new JedisRedisOps(jedisPool))
            .build();
    }

    @Test
    public void shouldAllowWithinWindow() {
        for (int i = 0; i < 5; i++) {
            assertTrue("Request " + i + " should be allowed", limiter.tryAcquire("it:fw:1"));
        }
    }

    @Test
    public void shouldDenyWhenWindowExhausted() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("it:fw:2");
        }
        assertFalse(limiter.tryAcquire("it:fw:2"));
    }

    @Test
    public void differentKeysShouldNotInterfere() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("it:fw:A");
        }
        assertTrue(limiter.tryAcquire("it:fw:B"));
    }

    @Test
    public void shouldResetAfterWindowPasses() throws InterruptedException {
        String key = "it:fw:reset";
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire(key);
        }
        assertFalse(limiter.tryAcquire(key));

        Thread.sleep(1100);

        assertTrue(limiter.tryAcquire(key));
    }

    @Test
    public void shouldRejectOversizedFirstRequest() {
        // 首次请求的 permits 超过 limit，应直接拒绝
        assertFalse(limiter.tryAcquire("it:fw:oversized", 10));
        // 窗口未创建，后续正常请求应通过
        assertTrue(limiter.tryAcquire("it:fw:oversized"));
    }
}
