package wangchongv8.myratelimiter.core;

/**
 * 限流异常，由阻塞式 {@link RateLimiter#acquire} 在等待超时时抛出。
 *
 * <p>调用方可以捕获此异常来实现降级逻辑：
 * <pre>{@code
 * try {
 *     limiter.acquire("key", 1, 2, TimeUnit.SECONDS);
 * } catch (RateLimitExceededException e) {
 *     fallback();
 * }
 * }</pre>
 */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
