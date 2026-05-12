package wangchongv8.myratelimiter.core;

import java.util.concurrent.TimeUnit;

/**
 * 分布式限流器的顶层接口。
 *
 * <p>提供非阻塞（{@code tryAcquire}）和阻塞（{@code acquire}）两种调用方式。
 * 每条限流对象由 {@code key} 标识，不同 key 之间互相独立。
 *
 * <p>Redis 异常会直接透传（fail-closed），调用方应保证 Redis 连接可用。
 *
 * <pre>{@code
 * RateLimiter limiter = new RateLimiterBuilder()
 *     .algorithm(Algorithm.TOKEN_BUCKET)
 *     .permits(100).perSecond(1)
 *     .redisOps(new JedisRedisOps(jedisPool))
 *     .build();
 *
 * // 非阻塞：立即返回 true/false
 * if (limiter.tryAcquire("user:123")) {
 *     doSomething();
 * }
 *
 * // 阻塞：等待直到获取许可或超时抛异常
 * limiter.acquire("user:123", 5, 2, TimeUnit.SECONDS);
 * }</pre>
 *
 * @author wangchongv8
 */
public interface RateLimiter {

    /**
     * 尝试获取 1 个许可，立即返回。
     *
     * @param key 限流对象的唯一标识，不能为 null
     * @return true 表示放行，false 表示被限流
     */
    boolean tryAcquire(String key);

    /**
     * 尝试获取指定数量的许可，立即返回。
     *
     * @param key     限流对象的唯一标识，不能为 null
     * @param permits 请求的许可数量，必须大于 0
     * @return true 表示放行，false 表示被限流
     */
    boolean tryAcquire(String key, int permits);

    /**
     * 阻塞获取 1 个许可，直到成功（无限等待）。
     *
     * @param key 限流对象的唯一标识，不能为 null
     * @throws RateLimitExceededException 线程被中断时抛出
     */
    void acquire(String key) throws RateLimitExceededException;

    /**
     * 阻塞获取指定数量的许可，直到成功（无限等待）。
     *
     * @param key     限流对象的唯一标识，不能为 null
     * @param permits 请求的许可数量，必须大于 0
     * @throws RateLimitExceededException 线程被中断时抛出
     */
    void acquire(String key, int permits) throws RateLimitExceededException;

    /**
     * 阻塞获取指定数量的许可，等待超时则抛出异常。
     *
     * @param key     限流对象的唯一标识，不能为 null
     * @param permits 请求的许可数量，必须大于 0
     * @param timeout 最大等待时间
     * @param unit    时间单位
     * @throws RateLimitExceededException 等待超时或线程被中断时抛出
     */
    void acquire(String key, int permits, long timeout, TimeUnit unit) throws RateLimitExceededException;
}
