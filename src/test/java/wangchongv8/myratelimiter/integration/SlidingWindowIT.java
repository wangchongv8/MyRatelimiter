package wangchongv8.myratelimiter.integration;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import redis.clients.jedis.JedisPool;
import wangchongv8.myratelimiter.core.Algorithm;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.builder.RateLimiterBuilder;
import wangchongv8.myratelimiter.redis.JedisRedisOps;

public class SlidingWindowIT {

    private JedisPool jedisPool;
    private RateLimiter limiter;

    @Before
    public void setUp() {
        jedisPool = new JedisPool("localhost", 6379);
        limiter = new RateLimiterBuilder()
            .algorithm(Algorithm.SLIDING_WINDOW)
            .permits(5)
            .perSecond(2)
            .redisOps(new JedisRedisOps(jedisPool))
            .build();
    }

    @After
    public void tearDown() {
        jedisPool.close();
    }

    @Test
    public void shouldAllowWithinWindow() {
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("it:sw:1"));
        }
    }

    @Test
    public void shouldDenyOverWindow() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("it:sw:2");
        }
        assertFalse(limiter.tryAcquire("it:sw:2"));
    }

    @Test
    public void shouldRecoverAfterWindowPasses() throws InterruptedException {
        String key = "it:sw:3";
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire(key);
        }
        assertFalse(limiter.tryAcquire(key));

        Thread.sleep(2100);

        assertTrue(limiter.tryAcquire(key));
    }
}
