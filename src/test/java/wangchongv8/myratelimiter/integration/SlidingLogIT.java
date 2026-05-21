package wangchongv8.myratelimiter.integration;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import wangchongv8.myratelimiter.core.Algorithm;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.builder.RateLimiterBuilder;
import wangchongv8.myratelimiter.redis.JedisRedisOps;

public class SlidingLogIT extends BaseRedisIT {

    private RateLimiter limiter;

    @Before
    public void setUp() {
        limiter = new RateLimiterBuilder()
            .algorithm(Algorithm.SLIDING_LOG)
            .permits(5)
            .perSecond(2)
            .redisOps(new JedisRedisOps(jedisPool))
            .build();
    }

    @Test
    public void shouldAllowWithinWindow() {
        for (int i = 0; i < 5; i++) {
            assertTrue("Request " + i + " should be allowed", limiter.tryAcquire("it:sl:1"));
        }
    }

    @Test
    public void shouldDenyOverWindow() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("it:sl:2");
        }
        assertFalse(limiter.tryAcquire("it:sl:2"));
    }

    @Test
    public void differentKeysShouldNotInterfere() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("it:sl:A");
        }
        assertTrue(limiter.tryAcquire("it:sl:B"));
    }

    @Test
    public void shouldRecoverAfterWindowPasses() throws InterruptedException {
        String key = "it:sl:recover";
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire(key);
        }
        assertFalse(limiter.tryAcquire(key));

        Thread.sleep(2100);

        assertTrue(limiter.tryAcquire(key));
    }

    @Test
    public void shouldGraduallyExpireOldestEntries() throws InterruptedException {
        String key = "it:sl:slide";

        // 第一批 3 条记录 (T≈0)
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.tryAcquire(key));
        }
        Thread.sleep(1800);

        // 第二批 2 条 (T≈1800ms)，占满 5 个配额
        for (int i = 0; i < 2; i++) {
            assertTrue(limiter.tryAcquire(key));
        }
        assertFalse(limiter.tryAcquire(key));

        // 再等 500ms：T≈2300ms，第一批的 3 条已滑出 2000ms 窗口
        Thread.sleep(500);

        // 第一批过期删除，第二批仍在 → 恢复 3 个额度
        for (int i = 0; i < 3; i++) {
            assertTrue("should allow " + i + " after oldest expired", limiter.tryAcquire(key));
        }
        assertFalse("should be at limit again", limiter.tryAcquire(key));
    }
}
