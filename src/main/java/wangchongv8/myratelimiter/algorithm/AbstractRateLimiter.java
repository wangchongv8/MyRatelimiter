package wangchongv8.myratelimiter.algorithm;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.core.RateLimitExceededException;
import wangchongv8.myratelimiter.redis.RedisOperations;

/**
 * 限流算法基类，提供参数校验、Lua 脚本加载、阻塞等待等公共逻辑。
 *
 * <p>子类只需实现 {@link #tryAcquire(String, int)}，其余方法由基类统一处理：
 * <ul>
 *   <li>{@link #tryAcquire(String)} 委托至 {@code tryAcquire(key, 1)}</li>
 *   <li>{@link #acquire} 系列方法基于 {@code tryAcquire} + 轮询实现阻塞等待</li>
 * </ul>
 */
public abstract class AbstractRateLimiter implements RateLimiter {
    /** 限流配置 */
    protected final RateLimiterConfig config;
    /** Redis 操作抽象 */
    protected final RedisOperations redisOps;

    protected AbstractRateLimiter(RateLimiterConfig config, RedisOperations redisOps) {
        this.config = config;
        this.redisOps = redisOps;
    }

    /**
     * 从 classpath 加载 Lua 脚本文件。
     *
     * @param path 脚本路径，如 "/lua/token_bucket.lua"
     * @return 脚本完整内容
     * @throws RuntimeException 脚本文件不存在或读取失败
     */
    protected static String loadScript(String path) {
        try (InputStream is = AbstractRateLimiter.class.getResourceAsStream(path);
             BufferedReader reader = new BufferedReader(
                 new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Lua script: " + path, e);
        }
    }

    /**
     * 参数校验：key 不能为 null，permits 必须大于 0。
     *
     * @throws IllegalArgumentException 参数不合法
     */
    protected void validate(String key, int permits) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be > 0");
        }
    }

    /** 尝试获取 1 个许可，委托至 {@link #tryAcquire(String, int)}。 */
    @Override
    public boolean tryAcquire(String key) {
        return tryAcquire(key, 1);
    }

    /** 无限期阻塞直到获取 1 个许可。 */
    @Override
    public void acquire(String key) {
        acquire(key, 1, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    /** 无限期阻塞直到获取指定数量的许可。 */
    @Override
    public void acquire(String key, int permits) {
        acquire(key, permits, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    /**
     * 估算获取指定数量许可的等待时间（毫秒）。默认返回 -1 表示无法估算，
     * 此时退化为指数退避。子类可以覆盖此方法提供精确估算。
     *
     * @param permits 请求的许可数量
     * @return 估算等待时间（毫秒），-1 表示无法估算
     */
    protected long estimateWaitTimeMs(int permits) {
        return -1;
    }

    /**
     * 阻塞获取许可。
     *
     * <p>如果算法可以提供等待时间估算（{@link #estimateWaitTimeMs} 返回正值），
     * 则使用 {@link LockSupport#parkNanos(long)} 精确等待后重试；
     * 否则使用指数退避（1ms→2ms→4ms…→200ms）。
     * 超时或中断时抛出 {@link RateLimitExceededException}。
     */
    @Override
    public void acquire(String key, int permits, long timeout, TimeUnit unit) {
        validate(key, permits);
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        long backoffMs = 1;

        while (true) {
            if (tryAcquire(key, permits)) {
                return;
            }

            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                throw new RateLimitExceededException(
                    "Rate limit exceeded for key: " + key);
            }

            long waitMs = estimateWaitTimeMs(permits);
            if (waitMs > 0) {
                if (waitMs - remaining > 50) {
                    throw new RateLimitExceededException(
                        "Rate limit exceeded for key: " + key);
                }
                waitMs = Math.min(waitMs, remaining);
            } else {
                waitMs = Math.min(backoffMs, remaining);
                backoffMs = Math.min(backoffMs * 2, 200);
            }

            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(waitMs));

            if (Thread.interrupted()) {
                throw new RateLimitExceededException(
                    "Interrupted while waiting for rate limit for key: " + key);
            }
        }
    }
}
