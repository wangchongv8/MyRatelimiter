package wangchongv8.myratelimiter.core;

public enum Algorithm {
    TOKEN_BUCKET,
    LEAKY_BUCKET,
    FIXED_WINDOW,
    SLIDING_WINDOW,
    SLIDING_LOG
}
