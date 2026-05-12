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

public class TokenBucketIT {

    private JedisPool jedisPool;
    private RateLimiter limiter;

    @Before
    public void setUp() {
        jedisPool = new JedisPool("localhost", 6379);
        limiter = new RateLimiterBuilder()
            .algorithm(Algorithm.TOKEN_BUCKET)
            .permits(5)
            .perSecond(1)
            .redisOps(new JedisRedisOps(jedisPool))
            .build();
    }

    @After
    public void tearDown() {
        jedisPool.close();
    }

    @Test
    public void shouldAllowWithinLimit() {
        for (int i = 0; i < 5; i++) {
            assertTrue("Request " + i + " should be allowed", limiter.tryAcquire("it:tb:1"));
        }
    }

    @Test
    public void shouldDenyOverLimit() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("it:tb:2");
        }
        assertFalse(limiter.tryAcquire("it:tb:2"));
    }

    @Test
    public void differentKeysShouldNotInterfere() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("it:tb:A");
        }
        assertTrue(limiter.tryAcquire("it:tb:B"));
    }
}
