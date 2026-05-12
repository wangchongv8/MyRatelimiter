# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test

```bash
mvn compile              # compile sources
mvn test                 # run unit tests (42 tests, no Redis needed)
mvn verify               # run unit + integration tests (needs Redis on localhost:6379)
mvn package -DskipTests  # build JAR
mvn test -Dtest=ClassName # run single test class
```

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

## Key Design Decisions

- **Fail-closed:** Redis exceptions propagate to caller as-is (no custom wrapping)
- **Redis connection:** User-supplied (`RedisOperations`), not managed by the library
- **Dependency scope:** Jedis and Redisson are `provided` — user brings their own
- **apikey prefix:** Default `"rl:"`, configurable via builder
- **Blocking acquire:** Polls every 10ms with exponential backoff, throws `RateLimitExceededException` on timeout

## User Preferences

- Java 8 for maximum compatibility
- Pure programmatic API (no annotations)
- All 5 algorithms available at call time
- Metrics export planned for future version (not implemented yet)
