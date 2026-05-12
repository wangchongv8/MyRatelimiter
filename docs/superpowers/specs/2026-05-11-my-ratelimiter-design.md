# MyRateLimiter Design Spec

## Overview

A Java 8 distributed rate limiter SDK backed by Redis. Supports 5 algorithms selectable at call time via pure programmatic API.

- **Language**: Java 8
- **Build**: Maven
- **GroupId**: `wangchongv8`
- **Distributed**: Redis (Lua scripts for atomicity)
- **Fail strategy**: fail-closed (Redis exceptions propagate to caller)

## Architecture

```
User code
   │
   ▼
┌─────────────────────────────────────┐
│         RateLimiterFactory          │  ← Entry, creates instance by Algorithm enum
│  .create(Algorithm, Config)         │
└─────────────────────────────────────┘
   │
   ▼
┌─────────────────────────────────────┐
│          RateLimiter (interface)      │
│  boolean tryAcquire(String key)     │
│  void acquire(String key,           │
│       long timeout, TimeUnit unit)  │
└─────────────────────────────────────┘
   │         │         │         │
   ▼         ▼         ▼         ▼
┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐
│Token │ │Leaky │ │Fixed │ │Slid..│  ← 5 strategy implementations
│Bucket│ │Bucket│ │Window│ │      │
└──┬───┘ └──┬───┘ └──┬───┘ └──┬───┘
   │         │         │         │
   └─────────┴─────────┴─────────┘
                  │
                  ▼
┌─────────────────────────────────────┐
│          RedisOperations            │  ← SPI, user-supplied
│  Long eval(script, key, args)       │
└─────────────────────────────────────┘
   │              │
   ▼              ▼
┌──────┐    ┌──────────┐
│Jedis │    │ Redisson │    ← Built-in adapters
│ Ops  │    │   Ops    │
└──────┘    └──────────┘
```

### Modules

| Module | Responsibility |
|--------|---------------|
| `core` | `RateLimiter` interface, `Algorithm` enum, `RateLimiterConfig` |
| `algorithm` | 5 strategy implementations, each embedding its own Lua script |
| `redis` | `RedisOperations` SPI + `JedisRedisOps` / `RedissonRedisOps` adapters |
| `builder` | `RateLimiterBuilder` (fluent) + `RateLimiterFactory` |

## API Design

### Configuration

```java
RateLimiterConfig config = RateLimiterConfig.builder()
    .permits(100)           // required
    .perSecond(1)           // or .perMinute(1), default 1 second
    .build();
```

### Builder

```java
RateLimiter limiter = RateLimiterBuilder
    .algorithm(Algorithm.TOKEN_BUCKET)
    .permits(100).perSecond(1)
    .redisOps(new JedisRedisOps(jedisPool))
    .build();
```

### Method Signatures

```java
public interface RateLimiter {
    // Non-blocking: returns true if permitted, false if limited
    boolean tryAcquire(String key);
    boolean tryAcquire(String key, int permits);

    // Blocking: waits up to timeout, throws on limit exceeded
    void acquire(String key) throws RateLimitExceededException;
    void acquire(String key, int permits) throws RateLimitExceededException;
    void acquire(String key, int permits, long timeout, TimeUnit unit)
        throws RateLimitExceededException;
}
```

### Config Parameters

| Parameter | Description | Default |
|-----------|-------------|---------|
| `permits` | Rate ceiling | Required |
| `perSecond` | Time window in seconds | 1 |
| `perMinute` | Time window in minutes | — |
| `redisKeyPrefix` | Redis key prefix | `"rl:"` |

## Supported Algorithms

| Algorithm | Enum | Lua Script Atomicity |
|-----------|------|---------------------|
| Token Bucket | `TOKEN_BUCKET` | Yes |
| Leaky Bucket | `LEAKY_BUCKET` | Yes |
| Fixed Window | `FIXED_WINDOW` | Yes |
| Sliding Window | `SLIDING_WINDOW` | Yes |
| Sliding Log | `SLIDING_LOG` | Yes |

## Redis Interaction

### SPI

```java
public interface RedisOperations {
    Long eval(String script, String key, List<String> args);
}
```

- Built-in adapters: `JedisRedisOps` (wraps `JedisPool`) and `RedissonRedisOps` (wraps `RedissonClient`)
- Users can implement `RedisOperations` to support other Redis clients (Lettuce, etc.)
- `evalsha` with automatic fallback to `eval` on `NOSCRIPT`

### Lua Script Lifecycle

1. Scripts stored as `.lua` files under `src/main/resources/lua/`
2. Each algorithm class loads its script at construction time
3. `RedisOperations.eval()` receives the raw script string + key + args
4. Script returns 1 (permitted) or 0 (denied)

### Fail Strategy

- **fail-closed**: Redis exceptions propagate to caller as-is (e.g. `JedisConnectionException`)
- No custom exception wrapping — Redis connectivity is the user's responsibility since they supply the connection

## Error Handling

| Scenario | Behavior |
|----------|----------|
| null key / permits <= 0 | `IllegalArgumentException` |
| Rate limit exceeded (non-blocking) | `tryAcquire` returns `false` |
| Rate limit exceeded + timeout (blocking) | `RateLimitExceededException` |
| Redis unavailable | Original Redis exception thrown (fail-closed) |
| Lua script NOSCRIPT | Auto-fallback to `eval`, transparent to caller |

## Testing

### Unit Tests (surefire, `*Test.java`)

- Mock `RedisOperations`
- Each algorithm: 10-15 test cases covering within-limit, over-limit, window rotation, concurrent permits
- Factory: config validation, correct type dispatching

### Integration Tests (failsafe, `*IT.java`)

- Real local Redis required
- Verify Lua script correctness
- Multi-instance concurrent access
- Redis restart / script reload behavior

### Dependencies

| Dependency | Scope |
|-----------|-------|
| Jedis | provided |
| Redisson | provided |
| JUnit 4 | test |
| Mockito | test |

## Scope

### In Scope
- 5 rate limiting algorithms with Redis + Lua
- `RedisOperations` SPI + Jedis/Redisson adapters
- `tryAcquire` (non-blocking) + `acquire` (blocking) APIs
- Fluent Builder
- Full unit + integration tests

### Out of Scope (Current Version)
- Annotation-driven API
- Reactive / async support
- Built-in connection pooling
- **Metrics export** (planned for future version) — expose rate limiter status (current permits, rejection count, Redis health) via Micrometer or similar

## Package Structure

```
wangchongv8.myratelimiter/
├── core/
│   ├── RateLimiter.java
│   ├── Algorithm.java
│   ├── RateLimiterConfig.java
│   └── RateLimitExceededException.java
├── algorithm/
│   ├── TokenBucketLimiter.java
│   ├── LeakyBucketLimiter.java
│   ├── FixedWindowLimiter.java
│   ├── SlidingWindowLimiter.java
│   └── SlidingLogLimiter.java
├── redis/
│   ├── RedisOperations.java
│   ├── JedisRedisOps.java
│   └── RedissonRedisOps.java
└── builder/
    ├── RateLimiterBuilder.java
    └── RateLimiterFactory.java
```
