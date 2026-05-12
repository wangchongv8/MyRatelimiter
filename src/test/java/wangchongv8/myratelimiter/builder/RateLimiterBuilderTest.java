package wangchongv8.myratelimiter.builder;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Test;
import wangchongv8.myratelimiter.core.Algorithm;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.redis.RedisOperations;

public class RateLimiterBuilderTest {

    @Test
    public void shouldBuildTokenBucketLimiter() {
        RedisOperations redisOps = mock(RedisOperations.class);
        RateLimiter limiter = new RateLimiterBuilder()
            .algorithm(Algorithm.TOKEN_BUCKET)
            .permits(100)
            .perSecond(2)
            .redisOps(redisOps)
            .build();

        assertNotNull(limiter);
    }

    @Test
    public void shouldBuildAllAlgorithmTypes() {
        RedisOperations redisOps = mock(RedisOperations.class);
        for (Algorithm algo : Algorithm.values()) {
            RateLimiter limiter = new RateLimiterBuilder()
                .algorithm(algo)
                .permits(10)
                .redisOps(redisOps)
                .build();
            assertNotNull("Failed to build " + algo, limiter);
        }
    }

    @Test
    public void shouldUseCustomKeyPrefix() {
        RedisOperations redisOps = mock(RedisOperations.class);
        RateLimiter limiter = new RateLimiterBuilder()
            .algorithm(Algorithm.FIXED_WINDOW)
            .permits(10)
            .redisKeyPrefix("custom:")
            .redisOps(redisOps)
            .build();

        assertNotNull(limiter);
        when(redisOps.eval(anyString(), eq("custom:test"), anyList())).thenReturn(1L);
        assertTrue(limiter.tryAcquire("test"));
    }

    @Test
    public void shouldUsePerMinute() {
        RedisOperations redisOps = mock(RedisOperations.class);
        RateLimiter limiter = new RateLimiterBuilder()
            .algorithm(Algorithm.TOKEN_BUCKET)
            .permits(100)
            .perMinute(1)
            .redisOps(redisOps)
            .build();

        assertNotNull(limiter);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectMissingAlgorithm() {
        new RateLimiterBuilder()
            .permits(10)
            .redisOps(mock(RedisOperations.class))
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectMissingRedisOps() {
        new RateLimiterBuilder()
            .algorithm(Algorithm.TOKEN_BUCKET)
            .permits(10)
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectInvalidPermits() {
        new RateLimiterBuilder()
            .algorithm(Algorithm.TOKEN_BUCKET)
            .permits(0)
            .redisOps(mock(RedisOperations.class))
            .build();
    }

    @Test
    public void shouldCreateViaFactory() {
        RedisOperations redisOps = mock(RedisOperations.class);
        RateLimiterConfig config = new RateLimiterConfig(10, 1, "rl:");
        RateLimiter limiter = RateLimiterFactory.create(Algorithm.SLIDING_WINDOW, config, redisOps);

        assertNotNull(limiter);
    }

    @Test(expected = IllegalArgumentException.class)
    public void factoryShouldRejectNullAlgorithm() {
        RateLimiterFactory.create(null, mock(RateLimiterConfig.class), mock(RedisOperations.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void factoryShouldRejectNullConfig() {
        RateLimiterFactory.create(Algorithm.TOKEN_BUCKET, null, mock(RedisOperations.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void factoryShouldRejectNullRedisOps() {
        RateLimiterFactory.create(Algorithm.TOKEN_BUCKET, mock(RateLimiterConfig.class), null);
    }
}
