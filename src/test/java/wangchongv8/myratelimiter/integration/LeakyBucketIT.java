package wangchongv8.myratelimiter.integration;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import wangchongv8.myratelimiter.core.Algorithm;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.builder.RateLimiterBuilder;
import wangchongv8.myratelimiter.redis.JedisRedisOps;

public class LeakyBucketIT extends BaseRedisIT {

    private RateLimiter limiter;

    @Before
    public void setUp() {
        limiter = new RateLimiterBuilder()
            .algorithm(Algorithm.LEAKY_BUCKET)
            .permits(5)
            .perSecond(1)
            .redisOps(new JedisRedisOps(jedisPool))
            .build();
    }

    @Test
    public void shouldAllowWithinCapacity() {
        for (int i = 0; i < 5; i++) {
            assertTrue("Request " + i + " should be allowed", limiter.tryAcquire("it:lb:1"));
        }
    }

    @Test
    public void shouldDenyWhenBucketFull() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("it:lb:2");
        }
        assertFalse(limiter.tryAcquire("it:lb:2"));
    }

    @Test
    public void differentKeysShouldNotInterfere() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("it:lb:A");
        }
        assertTrue(limiter.tryAcquire("it:lb:B"));
    }

    @Test
    public void shouldLeakAfterTimePasses() throws InterruptedException {
        String key = "it:lb:leak";
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire(key);
        }
        assertFalse(limiter.tryAcquire(key));

        Thread.sleep(1100);

        assertTrue(limiter.tryAcquire(key));
    }

    @Test
    public void shouldProcessSteadyRequests() throws InterruptedException {
        String key = "it:lb:steady";
        for (int i = 0; i < 10; i++) {
            assertTrue("Steady request " + i + " should pass", limiter.tryAcquire(key));
            Thread.sleep(250);
        }
    }

    @Test
    public void shouldPartiallyLeakAfterTimePasses() throws InterruptedException {
        String key = "it:lb:partial";
        // 占满桶
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(key));
        }
        assertFalse(limiter.tryAcquire(key));

        // 等 400ms，约漏出 5 * 0.4 = 2 个
        Thread.sleep(400);

        // 恢复 2 个额度，第 3 个拒绝
        assertTrue(limiter.tryAcquire(key));
        assertTrue(limiter.tryAcquire(key));
        assertFalse(limiter.tryAcquire(key));
    }
}
