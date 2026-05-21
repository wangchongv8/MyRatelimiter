# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test

```bash
mvn compile              # compile sources
mvn test                 # run unit tests (62 tests, no Redis needed)
mvn verify               # run unit (62) + integration tests (22), no external Redis needed
mvn package -DskipTests  # build JAR
mvn test -Dtest=ClassName # run single test class
```

**Test dependencies:** `jedis-mock` (pure Java fake Redis, supports Lua scripting) — integration tests require no native binaries.

## Architecture

Distributed rate limiter SDK (Java 8, Maven, groupId: `wangchongv8`).

**Pattern:** Strategy — each algorithm is an independent `RateLimiter` impl with embedded Redis Lua scripts. `RedisOperations` SPI abstracts the Redis client away.

```
core/          RateLimiter interface, Algorithm enum, RateLimiterConfig, RateLimitExceededException
algorithm/     AbstractRateLimiter + 5 strategies (TokenBucket, LeakyBucket, FixedWindow, SlidingWindow, SlidingLog)
redis/         RedisOperations SPI, JedisRedisOps (JedisPool), RedissonRedisOps (RedissonClient)
builder/       RateLimiterBuilder (fluent API), RateLimiterFactory
resources/lua/ 5 Lua scripts, loaded at class construction time
```

**Algorithm details:**
- TokenBucket / LeakyBucket: Redis Hash, O(1) per request
- FixedWindow: Redis String counter + TTL, O(1)
- SlidingWindow: Redis Hash with sub-window counters, O(sub-windows) ≈ O(10), bounded memory
- SlidingLog: Redis ZSET with per-request entries, O(log N) update, unbounded memory under high traffic

## Key Design Decisions

- **Fail-closed:** Redis exceptions propagate to caller as-is (no custom wrapping)
- **Redis connection:** User-supplied (`RedisOperations`), not managed by the library
- **Dependency scope:** Jedis and Redisson are `provided` — user brings their own
- **apikey prefix:** Default `"rl:"`, configurable via builder
- **Blocking acquire:** Polls every 10ms, throws `RateLimitExceededException` on timeout

## User Preferences

- Java 8 for maximum compatibility
- Pure programmatic API (no annotations)
- All 5 algorithms available at call time
- Metrics export planned for future version (not implemented yet)
