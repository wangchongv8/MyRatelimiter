# MyRateLimiter

Distributed rate limiter SDK backed by Redis, with 5 algorithms and a fluent builder API. Java 8 compatible.

## Setup

```xml
<dependency>
    <groupId>wangchongv8</groupId>
    <artifactId>my-ratelimiter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<!-- Bring your own Redis client (choose one) -->
<dependency>
    <groupId>redis.clients</groupId>
    <artifactId>jedis</artifactId>
    <version>3.10.0</version>
</dependency>
```

## Quick Start

```java
JedisPool jedisPool = new JedisPool("localhost", 6379);

RateLimiter limiter = new RateLimiterBuilder()
    .algorithm(Algorithm.TOKEN_BUCKET)
    .permits(100)
    .perSecond(1)
    .redisOps(new JedisRedisOps(jedisPool))
    .build();

// Non-blocking: immediate result
if (limiter.tryAcquire("user:123")) {
    doSomething();
}

// Blocking: wait up to 2 seconds for 5 permits
try {
    limiter.acquire("user:123", 5, 2, TimeUnit.SECONDS);
    doSomething();
} catch (RateLimitExceededException e) {
    handleRateLimited();
}
```

## Algorithms

详见 [docs/algorithms.md](docs/algorithms.md)

| Algorithm | Redis Structure | Best For |
|-----------|----------------|----------|
| `TOKEN_BUCKET` | Hash | Burst-tolerant traffic |
| `LEAKY_BUCKET` | Hash | Constant-rate shaping |
| `FIXED_WINDOW` | String counter | Simple counting, least overhead |
| `SLIDING_WINDOW` | Hash (sub-window counters) | Smoother than fixed window, bounded memory |
| `SLIDING_LOG` | ZSET | Exact per-request audit trail |

```java
// Token bucket — permits burst traffic
RateLimiter limiter = new RateLimiterBuilder()
    .algorithm(Algorithm.TOKEN_BUCKET)
    .permits(200).perMinute(1)
    .redisOps(new JedisRedisOps(jedisPool))
    .build();

// Sliding window — smooth rate limiting
RateLimiter limiter = new RateLimiterBuilder()
    .algorithm(Algorithm.SLIDING_WINDOW)
    .permits(50).perSecond(5)
    .redisOps(new JedisRedisOps(jedisPool))
    .build();
```

## Blocking vs Non-Blocking

```java
// Non-blocking — returns immediately
boolean allowed = limiter.tryAcquire("key");           // 1 permit
boolean allowed = limiter.tryAcquire("key", permits);  // N permits

// Blocking — waits indefinitely
limiter.acquire("key");                                 // 1 permit, no timeout
limiter.acquire("key", permits);                        // N permits, no timeout

// Blocking with timeout — throws RateLimitExceededException
limiter.acquire("key", permits, 2, TimeUnit.SECONDS);

// Handle rejection
try {
    limiter.acquire("key", 1, 500, TimeUnit.MILLISECONDS);
} catch (RateLimitExceededException e) {
    return fallbackResponse();
}
```

## Redis Client

Built-in adapters for Jedis and Redisson:

```java
// Jedis (connection pool)
JedisPool pool = new JedisPool("localhost", 6379);
RedisOperations ops = new JedisRedisOps(pool);

// Redisson
Config config = new Config();
config.useSingleServer().setAddress("redis://localhost:6379");
RedissonClient client = Redisson.create(config);
RedisOperations ops = new RedissonRedisOps(client);
```

Add custom client support by implementing `RedisOperations`:

```java
public class LettuceRedisOps implements RedisOperations {
    private final StatefulRedisConnection<String, String> conn;

    public LettuceRedisOps(StatefulRedisConnection<String, String> conn) {
        this.conn = conn;
    }

    @Override
    public Long eval(String script, String key, List<String> args) {
        return conn.sync().eval(script, ScriptOutputType.INTEGER,
            Collections.singletonList(key), args.toArray());
    }
}
```

## Key Isolation

```java
// Default prefix is "rl:"
// Key "user:123" → Redis key "rl:user:123"
limiter.tryAcquire("user:123");

// Custom prefix for environment isolation
RateLimiter limiter = new RateLimiterBuilder()
    .algorithm(Algorithm.FIXED_WINDOW)
    .permits(100)
    .redisKeyPrefix("prod:rl:")
    .redisOps(new JedisRedisOps(jedisPool))
    .build();
// Key "user:123" → Redis key "prod:rl:user:123"
```

## Factory API

Alternative to the builder for programmatic construction:

```java
RateLimiterConfig config = new RateLimiterConfig(100, 1, "rl:");
RateLimiter limiter = RateLimiterFactory.create(
    Algorithm.SLIDING_WINDOW, config, new JedisRedisOps(jedisPool));
```

## Build & Test

```bash
mvn compile           # compile sources
mvn test              # unit tests (62 tests, no Redis needed)
mvn verify            # unit + integration tests (84 total, no external Redis needed)
mvn package -DskipTests
```
