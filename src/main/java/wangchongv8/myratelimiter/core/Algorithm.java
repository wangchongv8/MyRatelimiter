package wangchongv8.myratelimiter.core;

/**
 * 限流算法枚举。
 *
 * <ul>
 *   <li>{@link #TOKEN_BUCKET} — 令牌桶，恒定速率填充令牌，突发流量可消耗积攒的令牌</li>
 *   <li>{@link #LEAKY_BUCKET} — 漏桶，恒定速率漏水，请求入桶，超过容量则拒绝</li>
 *   <li>{@link #FIXED_WINDOW} — 固定窗口，按固定时间窗口计数</li>
 *   <li>{@link #SLIDING_WINDOW} — 滑动窗口，基于时间滑动窗口计数，精度高于固定窗口</li>
 *   <li>{@link #SLIDING_LOG} — 滑动日志，记录每次请求时间戳，最精确但开销最大</li>
 * </ul>
 */
public enum Algorithm {
    /** 令牌桶 */
    TOKEN_BUCKET,
    /** 漏桶 */
    LEAKY_BUCKET,
    /** 固定窗口 */
    FIXED_WINDOW,
    /** 滑动窗口 */
    SLIDING_WINDOW,
    /** 滑动日志 */
    SLIDING_LOG
}
