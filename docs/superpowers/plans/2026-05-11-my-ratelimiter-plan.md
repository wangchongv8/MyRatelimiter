# MyRateLimiter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Java 8 distributed rate limiter SDK backed by Redis, supporting 5 algorithms selectable at call time via programmatic API.

**Architecture:** Strategy pattern — each algorithm is an independent `RateLimiter` implementation with embedded Lua scripts. A `RedisOperations` SPI abstracts the Redis client, with built-in Jedis/Redisson adapters. Factory + Builder provide the creation API.

**Tech Stack:** Java 8, Maven, Jedis/Redisson (provided scope), JUnit 4, Mockito, Redis Lua scripting

---

## File Map

| File | Responsibility |
|------|---------------|
| `pom.xml` | Maven config, dependencies, surefire+failsafe plugins |
| `core/Algorithm.java` | Enum of 5 algorithms |
| `core/RateLimiterConfig.java` | Immutable config: permits, interval, key prefix |
| `core/RateLimiter.java` | Top-level interface: `tryAcquire`, `acquire` |
| `core/RateLimitExceededException.java` | Thrown by blocking `acquire` on timeout |
| `redis/RedisOperations.java` | SPI: single `eval` method |
| `redis/JedisRedisOps.java` | JedisPool adapter, evalsha with NOSCRIPT fallback |
| `redis/RedissonRedisOps.java` | RedissonClient adapter |
| `algorithm/TokenBucketLimiter.java` | Token bucket strategy + Lua script |
| `algorithm/FixedWindowLimiter.java` | Fixed window strategy + Lua script |
| `algorithm/SlidingWindowLimiter.java` | Sliding window strategy + Lua script |
| `algorithm/LeakyBucketLimiter.java` | Leaky bucket strategy + Lua script |
| `algorithm/SlidingLogLimiter.java` | Sliding log strategy + Lua script |
| `builder/RateLimiterBuilder.java` | Fluent builder |
| `builder/RateLimiterFactory.java` | Maps Algorithm enum → concrete impl |

---

### Task 1: Project Scaffold

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/wangchongv8/myratelimiter/` (dirs)
- Create: `src/test/java/wangchongv8/myratelimiter/` (dirs)
- Create: `src/main/resources/lua/` (dir)

- [ ] **Step 1: Write pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>wangchongv8</groupId>
    <artifactId>my-ratelimiter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <name>MyRateLimiter</name>
    <description>Distributed rate limiter SDK backed by Redis</description>

    <properties>
        <java.version>1.8</java.version>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>redis.clients</groupId>
            <artifactId>jedis</artifactId>
            <version>3.10.0</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.redisson</groupId>
            <artifactId>redisson</artifactId>
            <version>3.17.7</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <version>4.13.2</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
            <version>3.12.4</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.0.0-M7</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
                <version>3.0.0-M7</version>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create directory structure**

```bash
mkdir -p src/main/java/wangchongv8/myratelimiter/{core,algorithm,redis,builder}
mkdir -p src/main/resources/lua
mkdir -p src/test/java/wangchongv8/myratelimiter/{algorithm,redis,builder,integration}
```

- [ ] **Step 3: Verify project compiles**

```bash
mvn compile
```

Expected: BUILD SUCCESS (no source files yet, but pom parses correctly)

- [ ] **Step 4: Commit**

```bash
git add pom.xml
git commit -m "feat: initialize Maven project scaffold"
```

---

### Task 2: Core Types

**Files:**
- Create: `src/main/java/wangchongv8/myratelimiter/core/Algorithm.java`
- Create: `src/main/java/wangchongv8/myratelimiter/core/RateLimiterConfig.java`
- Create: `src/main/java/wangchongv8/myratelimiter/core/RateLimiter.java`
- Create: `src/main/java/wangchongv8/myratelimiter/core/RateLimitExceededException.java`

- [ ] **Step 1: Write Algorithm enum**

```java
package wangchongv8.myratelimiter.core;

public enum Algorithm {
    TOKEN_BUCKET,
    LEAKY_BUCKET,
    FIXED_WINDOW,
    SLIDING_WINDOW,
    SLIDING_LOG
}
```

- [ ] **Step 2: Write RateLimiterConfig**

```java
package wangchongv8.myratelimiter.core;

import java.util.Objects;

public class RateLimiterConfig {
    private final int permits;
    private final int intervalSeconds;
    private final String redisKeyPrefix;

    public RateLimiterConfig(int permits, int intervalSeconds, String redisKeyPrefix) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be > 0");
        }
        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException("intervalSeconds must be > 0");
        }
        Objects.requireNonNull(redisKeyPrefix, "redisKeyPrefix must not be null");
        this.permits = permits;
        this.intervalSeconds = intervalSeconds;
        this.redisKeyPrefix = redisKeyPrefix;
    }

    public int getPermits() { return permits; }
    public int getIntervalSeconds() { return intervalSeconds; }
    public String getRedisKeyPrefix() { return redisKeyPrefix; }
}
```

- [ ] **Step 3: Write RateLimitExceededException**

```java
package wangchongv8.myratelimiter.core;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
```

- [ ] **Step 4: Write RateLimiter interface**

```java
package wangchongv8.myratelimiter.core;

import java.util.concurrent.TimeUnit;

public interface RateLimiter {
    boolean tryAcquire(String key);
    boolean tryAcquire(String key, int permits);
    void acquire(String key) throws RateLimitExceededException;
    void acquire(String key, int permits) throws RateLimitExceededException;
    void acquire(String key, int permits, long timeout, TimeUnit unit) throws RateLimitExceededException;
}
```

- [ ] **Step 5: Verify compilation**

```bash
mvn compile
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/wangchongv8/myratelimiter/core/
git commit -m "feat: add core types - Algorithm, Config, RateLimiter interface"
```

---

### Task 3: RedisOperations SPI + Jedis Adapter

**Files:**
- Create: `src/main/java/wangchongv8/myratelimiter/redis/RedisOperations.java`
- Create: `src/main/java/wangchongv8/myratelimiter/redis/JedisRedisOps.java`
- Create: `src/test/java/wangchongv8/myratelimiter/redis/JedisRedisOpsTest.java`

- [ ] **Step 1: Write RedisOperations interface**

```java
package wangchongv8.myratelimiter.redis;

import java.util.List;

public interface RedisOperations {
    Long eval(String script, String key, List<String> args);
}
```

- [ ] **Step 2: Write failing test for JedisRedisOps**

```java
package wangchongv8.myratelimiter.redis;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisNoScriptException;

public class JedisRedisOpsTest {

    private JedisPool pool;
    private Jedis jedis;
    private JedisRedisOps ops;

    @Before
    public void setUp() {
        pool = mock(JedisPool.class);
        jedis = mock(Jedis.class);
        when(pool.getResource()).thenReturn(jedis);
        ops = new JedisRedisOps(pool);
    }

    @Test
    public void shouldEvalScriptSuccessfully() {
        when(jedis.evalsha(anyString(), anyList(), anyList())).thenReturn(1L);

        Long result = ops.eval("return 1", "mykey", Collections.singletonList("100"));

        assertEquals(Long.valueOf(1), result);
        verify(jedis).close();
    }

    @Test
    public void shouldFallbackToEvalOnNoScript() {
        when(jedis.evalsha(anyString(), anyList(), anyList()))
            .thenThrow(new JedisNoScriptException("NOSCRIPT"))
            .thenReturn(1L);
        when(jedis.eval(anyString(), anyList(), anyList())).thenReturn(1L);

        Long result = ops.eval("return 1", "mykey", Collections.singletonList("100"));

        assertEquals(Long.valueOf(1), result);
    }

    @Test
    public void shouldPropagateJedisException() {
        when(jedis.evalsha(anyString(), anyList(), anyList()))
            .thenThrow(new JedisNoScriptException("NOSCRIPT"));
        when(jedis.eval(anyString(), anyList(), anyList()))
            .thenThrow(new RuntimeException("connection lost"));

        try {
            ops.eval("return 1", "mykey", Collections.singletonList("100"));
            fail("expected exception");
        } catch (RuntimeException e) {
            assertEquals("connection lost", e.getMessage());
        }
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

```bash
mvn test -pl . -Dtest=JedisRedisOpsTest
```

Expected: COMPILATION ERROR (JedisRedisOps not found)

- [ ] **Step 4: Write JedisRedisOps implementation**

```java
package wangchongv8.myratelimiter.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisNoScriptException;

public class JedisRedisOps implements RedisOperations {
    private final JedisPool jedisPool;

    public JedisRedisOps(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    @Override
    public Long eval(String script, String key, List<String> args) {
        String sha = sha1(script);
        try (Jedis jedis = jedisPool.getResource()) {
            try {
                return (Long) jedis.evalsha(sha, Collections.singletonList(key), args);
            } catch (JedisNoScriptException e) {
                return (Long) jedis.eval(script, Collections.singletonList(key), args);
            }
        }
    }

    private static String sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 not available", e);
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
mvn test -pl . -Dtest=JedisRedisOpsTest
```

Expected: Tests pass (4 tests — shouldEvalScriptSuccessfully, shouldFallbackToEvalOnNoScript, shouldPropagateJedisException)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/wangchongv8/myratelimiter/redis/ src/test/java/wangchongv8/myratelimiter/redis/
git commit -m "feat: add RedisOperations SPI and JedisRedisOps adapter"
```

---

### Task 4: Token Bucket Algorithm

**Files:**
- Create: `src/main/resources/lua/token_bucket.lua`
- Create: `src/main/java/wangchongv8/myratelimiter/algorithm/TokenBucketLimiter.java`
- Create: `src/test/java/wangchongv8/myratelimiter/algorithm/TokenBucketLimiterTest.java`

- [ ] **Step 1: Write token_bucket.lua**

```lua
-- KEYS[1]: bucket key
-- ARGV[1]: max tokens (capacity)
-- ARGV[2]: refill rate (tokens per second)
-- ARGV[3]: requested permits
-- ARGV[4]: current time in milliseconds
-- Returns: 1 if allowed, 0 if denied

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])
local now = tonumber(ARGV[4])

local bucket = redis.call('HMGET', key, 'tokens', 'last_refill_time')
local tokens = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

if tokens == nil then
    tokens = capacity
    last_refill = now
end

local elapsed_ms = now - last_refill
if elapsed_ms > 0 then
    local refill = (elapsed_ms / 1000.0) * rate
    tokens = math.min(capacity, tokens + refill)
end

if tokens >= requested then
    tokens = tokens - requested
    redis.call('HMSET', key, 'tokens', tokens, 'last_refill_time', now)
    local ttl = math.ceil(capacity / rate) + 1
    if ttl < 1 then ttl = 1 end
    redis.call('EXPIRE', key, ttl)
    return 1
end

return 0
```

- [ ] **Step 2: Write failing test for TokenBucketLimiter**

```java
package wangchongv8.myratelimiter.algorithm;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.redis.RedisOperations;

public class TokenBucketLimiterTest {

    private RedisOperations redisOps;
    private RateLimiter limiter;
    private RateLimiterConfig config;

    @Before
    public void setUp() {
        redisOps = mock(RedisOperations.class);
        config = new RateLimiterConfig(10, 1, "rl:");
        limiter = new TokenBucketLimiter(config, redisOps);
    }

    @Test
    public void shouldAllowRequestWithinLimit() {
        when(redisOps.eval(anyString(), eq("rl:user:1"), anyList())).thenReturn(1L);

        boolean result = limiter.tryAcquire("user:1");

        assertTrue(result);
    }

    @Test
    public void shouldDenyRequestOverLimit() {
        when(redisOps.eval(anyString(), eq("rl:user:1"), anyList())).thenReturn(0L);

        boolean result = limiter.tryAcquire("user:1");

        assertFalse(result);
    }

    @Test
    public void shouldPassCorrectArgsToLua() {
        ArgumentCaptor<List<String>> argsCaptor = ArgumentCaptor.forClass(List.class);
        when(redisOps.eval(anyString(), eq("rl:user:1"), argsCaptor.capture())).thenReturn(1L);

        limiter.tryAcquire("user:1", 3);

        List<String> args = argsCaptor.getValue();
        assertEquals("10", args.get(0));  // capacity
        assertEquals("10.0", args.get(1)); // rate = 10/1s = 10 tokens/sec
        assertEquals("3", args.get(2));   // requested permits
    }

    @Test
    public void shouldUseDefaultPermitsForTryAcquire() {
        ArgumentCaptor<List<String>> argsCaptor = ArgumentCaptor.forClass(List.class);
        when(redisOps.eval(anyString(), eq("rl:user:1"), argsCaptor.capture())).thenReturn(1L);

        limiter.tryAcquire("user:1");

        List<String> args = argsCaptor.getValue();
        assertEquals("1", args.get(2));   // default 1 permit
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNullKey() {
        limiter.tryAcquire(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectZeroPermits() {
        limiter.tryAcquire("user:1", 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNegativePermits() {
        limiter.tryAcquire("user:1", -1);
    }

    @Test
    public void shouldPropagateRedisException() {
        when(redisOps.eval(anyString(), anyString(), anyList()))
            .thenThrow(new RuntimeException("redis down"));

        try {
            limiter.tryAcquire("user:1");
            fail("expected exception");
        } catch (RuntimeException e) {
            assertEquals("redis down", e.getMessage());
        }
    }

    @Test
    public void shouldAcquireBlockUntilAllowed() {
        when(redisOps.eval(anyString(), eq("rl:user:1"), anyList())).thenReturn(1L);

        limiter.acquire("user:1");  // should not throw

        // verify called
        verify(redisOps).eval(anyString(), eq("rl:user:1"), anyList());
    }

    @Test
    public void shouldAcquireThrowOnTimeout() {
        when(redisOps.eval(anyString(), eq("rl:user:1"), anyList())).thenReturn(0L);

        long start = System.currentTimeMillis();
        try {
            limiter.acquire("user:1", 1, 200, java.util.concurrent.TimeUnit.MILLISECONDS);
            fail("expected RateLimitExceededException");
        } catch (wangchongv8.myratelimiter.core.RateLimitExceededException e) {
            long elapsed = System.currentTimeMillis() - start;
            assertTrue(elapsed >= 200);
        }
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

```bash
mvn test -pl . -Dtest=TokenBucketLimiterTest
```

Expected: COMPILATION ERROR (TokenBucketLimiter not found)

- [ ] **Step 4: Write TokenBucketLimiter implementation**

```java
package wangchongv8.myratelimiter.algorithm;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.core.RateLimitExceededException;
import wangchongv8.myratelimiter.redis.RedisOperations;

public class TokenBucketLimiter implements RateLimiter {
    private static final String LUA_SCRIPT = loadScript("/lua/token_bucket.lua");

    private final RateLimiterConfig config;
    private final RedisOperations redisOps;
    private final double rate;

    public TokenBucketLimiter(RateLimiterConfig config, RedisOperations redisOps) {
        this.config = config;
        this.redisOps = redisOps;
        this.rate = (double) config.getPermits() / config.getIntervalSeconds();
    }

    @Override
    public boolean tryAcquire(String key) {
        return tryAcquire(key, 1);
    }

    @Override
    public boolean tryAcquire(String key, int permits) {
        validate(key, permits);
        String redisKey = config.getRedisKeyPrefix() + key;
        List<String> args = Arrays.asList(
            String.valueOf(config.getPermits()),
            String.valueOf(rate),
            String.valueOf(permits),
            String.valueOf(System.currentTimeMillis())
        );
        Long result = redisOps.eval(LUA_SCRIPT, redisKey, args);
        return result != null && result == 1L;
    }

    @Override
    public void acquire(String key) {
        acquire(key, 1, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    @Override
    public void acquire(String key, int permits) {
        acquire(key, permits, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    @Override
    public void acquire(String key, int permits, long timeout, TimeUnit unit) {
        validate(key, permits);
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (true) {
            if (tryAcquire(key, permits)) {
                return;
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new RateLimitExceededException(
                    "Rate limit exceeded for key: " + key);
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RateLimitExceededException("Interrupted while waiting for rate limit");
            }
        }
    }

    private void validate(String key, int permits) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be > 0");
        }
    }

    private static String loadScript(String path) {
        try (InputStream is = TokenBucketLimiter.class.getResourceAsStream(path);
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
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
mvn test -pl . -Dtest=TokenBucketLimiterTest
```

Expected: All 10 tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/lua/token_bucket.lua \
        src/main/java/wangchongv8/myratelimiter/algorithm/TokenBucketLimiter.java \
        src/test/java/wangchongv8/myratelimiter/algorithm/TokenBucketLimiterTest.java
git commit -m "feat: add token bucket rate limiter"
```

---

### Task 5: Fixed Window Algorithm

**Files:**
- Create: `src/main/resources/lua/fixed_window.lua`
- Create: `src/main/java/wangchongv8/myratelimiter/algorithm/FixedWindowLimiter.java`
- Create: `src/test/java/wangchongv8/myratelimiter/algorithm/FixedWindowLimiterTest.java`

- [ ] **Step 1: Write fixed_window.lua**

```lua
-- KEYS[1]: window key
-- ARGV[1]: window size in seconds
-- ARGV[2]: max requests per window
-- Returns: 1 if allowed, 0 if denied

local key = KEYS[1]
local window = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])

local current = redis.call('GET', key)
if current == false then
    redis.call('SET', key, requested, 'EX', window)
    return 1
end

current = tonumber(current)
if current + requested <= limit then
    redis.call('INCRBY', key, requested)
    return 1
end

return 0
```

- [ ] **Step 2: Write failing test for FixedWindowLimiter**

```java
package wangchongv8.myratelimiter.algorithm;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.redis.RedisOperations;

public class FixedWindowLimiterTest {

    private RedisOperations redisOps;
    private RateLimiter limiter;

    @Before
    public void setUp() {
        redisOps = mock(RedisOperations.class);
        RateLimiterConfig config = new RateLimiterConfig(5, 60, "rl:");
        limiter = new FixedWindowLimiter(config, redisOps);
    }

    @Test
    public void shouldAllowRequestWithinWindow() {
        when(redisOps.eval(anyString(), eq("rl:user:1"), anyList())).thenReturn(1L);
        assertTrue(limiter.tryAcquire("user:1"));
    }

    @Test
    public void shouldDenyWhenWindowExhausted() {
        when(redisOps.eval(anyString(), eq("rl:user:1"), anyList())).thenReturn(0L);
        assertFalse(limiter.tryAcquire("user:1"));
    }

    @Test
    public void shouldPassWindowSizeInArgs() {
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        when(redisOps.eval(anyString(), eq("rl:user:1"), captor.capture())).thenReturn(1L);

        limiter.tryAcquire("user:1", 2);

        List<String> args = captor.getValue();
        assertEquals("60", args.get(0));  // 60 second window
        assertEquals("5", args.get(1));   // max 5 per window
        assertEquals("2", args.get(2));   // requested permits
    }

    @Test
    public void shouldRejectNullKey() {
        try {
            limiter.tryAcquire(null);
            fail("expected exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

```bash
mvn test -pl . -Dtest=FixedWindowLimiterTest
```

Expected: COMPILATION ERROR

- [ ] **Step 4: Write FixedWindowLimiter**

```java
package wangchongv8.myratelimiter.algorithm;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.core.RateLimitExceededException;
import wangchongv8.myratelimiter.redis.RedisOperations;

public class FixedWindowLimiter implements RateLimiter {
    private static final String LUA_SCRIPT = loadScript("/lua/fixed_window.lua");

    private final RateLimiterConfig config;
    private final RedisOperations redisOps;

    public FixedWindowLimiter(RateLimiterConfig config, RedisOperations redisOps) {
        this.config = config;
        this.redisOps = redisOps;
    }

    @Override
    public boolean tryAcquire(String key) {
        return tryAcquire(key, 1);
    }

    @Override
    public boolean tryAcquire(String key, int permits) {
        if (key == null) throw new IllegalArgumentException("key must not be null");
        if (permits <= 0) throw new IllegalArgumentException("permits must be > 0");

        String redisKey = config.getRedisKeyPrefix() + key;
        List<String> args = Arrays.asList(
            String.valueOf(config.getIntervalSeconds()),
            String.valueOf(config.getPermits()),
            String.valueOf(permits)
        );
        Long result = redisOps.eval(LUA_SCRIPT, redisKey, args);
        return result != null && result == 1L;
    }

    @Override
    public void acquire(String key) { tryAcquire(key); }

    @Override
    public void acquire(String key, int permits) { tryAcquire(key, permits); }

    @Override
    public void acquire(String key, int permits, long timeout, TimeUnit unit) {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (!tryAcquire(key, permits)) {
            if (System.currentTimeMillis() >= deadline) {
                throw new RateLimitExceededException("Rate limit exceeded for key: " + key);
            }
            try { Thread.sleep(10); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RateLimitExceededException("Interrupted");
            }
        }
    }

    private static String loadScript(String path) {
        try (InputStream is = FixedWindowLimiter.class.getResourceAsStream(path);
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
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
mvn test -pl . -Dtest=FixedWindowLimiterTest
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/lua/fixed_window.lua \
        src/main/java/wangchongv8/myratelimiter/algorithm/FixedWindowLimiter.java \
        src/test/java/wangchongv8/myratelimiter/algorithm/FixedWindowLimiterTest.java
git commit -m "feat: add fixed window rate limiter"
```

---

### Task 6: Sliding Window Algorithm

**Files:**
- Create: `src/main/resources/lua/sliding_window.lua`
- Create: `src/main/java/wangchongv8/myratelimiter/algorithm/SlidingWindowLimiter.java`
- Create: `src/test/java/wangchongv8/myratelimiter/algorithm/SlidingWindowLimiterTest.java`

- [ ] **Step 1: Write sliding_window.lua**

```lua
-- KEYS[1]: sorted set key
-- ARGV[1]: window size in milliseconds
-- ARGV[2]: max requests per window
-- ARGV[3]: current time in milliseconds
-- ARGV[4]: unique request id (nanoseconds as string)
-- ARGV[5]: requested permits (count to add)
-- Returns: 1 if allowed, 0 if denied

local key = KEYS[1]
local window = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local member_base = ARGV[4]
local requested = tonumber(ARGV[5])

-- Remove expired entries
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

local count = redis.call('ZCARD', key)
if count + requested <= limit then
    for i = 1, requested do
        redis.call('ZADD', key, now, member_base .. ':' .. i)
    end
    redis.call('PEXPIRE', key, window + 1000)
    return 1
end

return 0
```

- [ ] **Step 2: Write failing test for SlidingWindowLimiter**

```java
package wangchongv8.myratelimiter.algorithm;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.redis.RedisOperations;

public class SlidingWindowLimiterTest {

    private RedisOperations redisOps;
    private RateLimiter limiter;

    @Before
    public void setUp() {
        redisOps = mock(RedisOperations.class);
        RateLimiterConfig config = new RateLimiterConfig(10, 1, "rl:");
        limiter = new SlidingWindowLimiter(config, redisOps);
    }

    @Test
    public void shouldAllowRequestWithinWindow() {
        when(redisOps.eval(anyString(), eq("rl:user:1"), anyList())).thenReturn(1L);
        assertTrue(limiter.tryAcquire("user:1"));
    }

    @Test
    public void shouldDenyWhenWindowExhausted() {
        when(redisOps.eval(anyString(), eq("rl:user:1"), anyList())).thenReturn(0L);
        assertFalse(limiter.tryAcquire("user:1"));
    }

    @Test
    public void shouldPassWindowSizeInMillis() {
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        when(redisOps.eval(anyString(), eq("rl:user:1"), captor.capture())).thenReturn(1L);

        limiter.tryAcquire("user:1", 3);

        List<String> args = captor.getValue();
        assertEquals("1000", args.get(0));  // 1 second in millis
        assertEquals("10", args.get(1));    // limit
        // args.get(2) is current time (dynamic)
        // args.get(3) is unique id (dynamic)
        assertEquals("3", args.get(4));      // requested permits
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNullKey() {
        limiter.tryAcquire(null);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

```bash
mvn test -pl . -Dtest=SlidingWindowLimiterTest
```

Expected: COMPILATION ERROR

- [ ] **Step 4: Write SlidingWindowLimiter** (same structure as FixedWindowLimiter, but passes window in millis and generates unique member IDs)

```java
package wangchongv8.myratelimiter.algorithm;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.core.RateLimitExceededException;
import wangchongv8.myratelimiter.redis.RedisOperations;

public class SlidingWindowLimiter implements RateLimiter {
    private static final String LUA_SCRIPT = loadScript("/lua/sliding_window.lua");

    private final RateLimiterConfig config;
    private final RedisOperations redisOps;

    public SlidingWindowLimiter(RateLimiterConfig config, RedisOperations redisOps) {
        this.config = config;
        this.redisOps = redisOps;
    }

    @Override
    public boolean tryAcquire(String key) {
        return tryAcquire(key, 1);
    }

    @Override
    public boolean tryAcquire(String key, int permits) {
        if (key == null) throw new IllegalArgumentException("key must not be null");
        if (permits <= 0) throw new IllegalArgumentException("permits must be > 0");

        long now = System.currentTimeMillis();
        String redisKey = config.getRedisKeyPrefix() + key;
        List<String> args = Arrays.asList(
            String.valueOf(config.getIntervalSeconds() * 1000L),
            String.valueOf(config.getPermits()),
            String.valueOf(now),
            String.valueOf(System.nanoTime()),
            String.valueOf(permits)
        );
        Long result = redisOps.eval(LUA_SCRIPT, redisKey, args);
        return result != null && result == 1L;
    }

    @Override
    public void acquire(String key) { tryAcquire(key); }

    @Override
    public void acquire(String key, int permits) { tryAcquire(key, permits); }

    @Override
    public void acquire(String key, int permits, long timeout, TimeUnit unit) {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (!tryAcquire(key, permits)) {
            if (System.currentTimeMillis() >= deadline) {
                throw new RateLimitExceededException("Rate limit exceeded for key: " + key);
            }
            try { Thread.sleep(10); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RateLimitExceededException("Interrupted");
            }
        }
    }

    private static String loadScript(String path) {
        try (InputStream is = SlidingWindowLimiter.class.getResourceAsStream(path);
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
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
mvn test -pl . -Dtest=SlidingWindowLimiterTest
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/lua/sliding_window.lua \
        src/main/java/wangchongv8/myratelimiter/algorithm/SlidingWindowLimiter.java \
        src/test/java/wangchongv8/myratelimiter/algorithm/SlidingWindowLimiterTest.java
git commit -m "feat: add sliding window rate limiter"
```

---

### Task 7: Leaky Bucket Algorithm

**Files:**
- Create: `src/main/resources/lua/leaky_bucket.lua`
- Create: `src/main/java/wangchongv8/myratelimiter/algorithm/LeakyBucketLimiter.java`
- Create: `src/test/java/wangchongv8/myratelimiter/algorithm/LeakyBucketLimiterTest.java`

- [ ] **Step 1: Write leaky_bucket.lua**

```lua
-- KEYS[1]: bucket key
-- ARGV[1]: capacity (max water level)
-- ARGV[2]: leak rate (requests leaked per second)
-- ARGV[3]: requested permits
-- ARGV[4]: current time in milliseconds
-- Returns: 1 if allowed, 0 if denied

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])
local now = tonumber(ARGV[4])

local bucket = redis.call('HMGET', key, 'water', 'last_leak_time')
local water = tonumber(bucket[1])
local last_leak = tonumber(bucket[2])

if water == nil then
    water = 0
    last_leak = now
end

local elapsed_ms = now - last_leak
if elapsed_ms > 0 then
    local leaked = (elapsed_ms / 1000.0) * rate
    water = math.max(0, water - leaked)
end

if water + requested <= capacity then
    water = water + requested
    redis.call('HMSET', key, 'water', water, 'last_leak_time', now)
    local ttl = math.ceil(capacity / rate) + 1
    if ttl < 1 then ttl = 1 end
    redis.call('EXPIRE', key, ttl)
    return 1
end

redis.call('HSET', key, 'last_leak_time', now)
return 0
```

- [ ] **Step 2: Write LeakyBucketLimiterTest** (same pattern as TokenBucket, verifying leak semantics)

```java
package wangchongv8.myratelimiter.algorithm;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.redis.RedisOperations;

public class LeakyBucketLimiterTest {

    private RedisOperations redisOps;
    private RateLimiter limiter;

    @Before
    public void setUp() {
        redisOps = mock(RedisOperations.class);
        RateLimiterConfig config = new RateLimiterConfig(10, 1, "rl:");
        limiter = new LeakyBucketLimiter(config, redisOps);
    }

    @Test
    public void shouldAllowRequestWithinCapacity() {
        when(redisOps.eval(anyString(), eq("rl:user:1"), anyList())).thenReturn(1L);
        assertTrue(limiter.tryAcquire("user:1"));
    }

    @Test
    public void shouldDenyWhenBucketFull() {
        when(redisOps.eval(anyString(), eq("rl:user:1"), anyList())).thenReturn(0L);
        assertFalse(limiter.tryAcquire("user:1"));
    }

    @Test
    public void shouldPassLeakRateInArgs() {
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        when(redisOps.eval(anyString(), eq("rl:user:1"), captor.capture())).thenReturn(1L);

        limiter.tryAcquire("user:1", 2);

        List<String> args = captor.getValue();
        assertEquals("10", args.get(0));   // capacity
        assertEquals("10.0", args.get(1)); // leak rate = permits/seconds
        assertEquals("2", args.get(2));    // requested
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNullKey() {
        limiter.tryAcquire(null);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

```bash
mvn test -pl . -Dtest=LeakyBucketLimiterTest
```

- [ ] **Step 4: Write LeakyBucketLimiter** (same pattern as TokenBucketLimiter, referencing leaky_bucket.lua)

```java
package wangchongv8.myratelimiter.algorithm;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.core.RateLimitExceededException;
import wangchongv8.myratelimiter.redis.RedisOperations;

public class LeakyBucketLimiter implements RateLimiter {
    private static final String LUA_SCRIPT = loadScript("/lua/leaky_bucket.lua");

    private final RateLimiterConfig config;
    private final RedisOperations redisOps;
    private final double rate;

    public LeakyBucketLimiter(RateLimiterConfig config, RedisOperations redisOps) {
        this.config = config;
        this.redisOps = redisOps;
        this.rate = (double) config.getPermits() / config.getIntervalSeconds();
    }

    @Override
    public boolean tryAcquire(String key) { return tryAcquire(key, 1); }

    @Override
    public boolean tryAcquire(String key, int permits) {
        if (key == null) throw new IllegalArgumentException("key must not be null");
        if (permits <= 0) throw new IllegalArgumentException("permits must be > 0");
        String redisKey = config.getRedisKeyPrefix() + key;
        List<String> args = Arrays.asList(
            String.valueOf(config.getPermits()),
            String.valueOf(rate),
            String.valueOf(permits),
            String.valueOf(System.currentTimeMillis())
        );
        Long result = redisOps.eval(LUA_SCRIPT, redisKey, args);
        return result != null && result == 1L;
    }

    @Override
    public void acquire(String key) { tryAcquire(key); }

    @Override
    public void acquire(String key, int permits) { tryAcquire(key, permits); }

    @Override
    public void acquire(String key, int permits, long timeout, TimeUnit unit) {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (!tryAcquire(key, permits)) {
            if (System.currentTimeMillis() >= deadline) {
                throw new RateLimitExceededException("Rate limit exceeded for key: " + key);
            }
            try { Thread.sleep(10); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RateLimitExceededException("Interrupted");
            }
        }
    }

    private static String loadScript(String path) {
        try (InputStream is = LeakyBucketLimiter.class.getResourceAsStream(path);
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
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
mvn test -pl . -Dtest=LeakyBucketLimiterTest
```

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/lua/leaky_bucket.lua \
        src/main/java/wangchongv8/myratelimiter/algorithm/LeakyBucketLimiter.java \
        src/test/java/wangchongv8/myratelimiter/algorithm/LeakyBucketLimiterTest.java
git commit -m "feat: add leaky bucket rate limiter"
```

---

### Task 8: Sliding Log Algorithm

**Files:**
- Create: `src/main/resources/lua/sliding_log.lua`
- Create: `src/main/java/wangchongv8/myratelimiter/algorithm/SlidingLogLimiter.java`
- Create: `src/test/java/wangchongv8/myratelimiter/algorithm/SlidingLogLimiterTest.java`

- [ ] **Step 1: Write sliding_log.lua**

```lua
-- KEYS[1]: sorted set key
-- ARGV[1]: window size in milliseconds
-- ARGV[2]: max requests per window
-- ARGV[3]: current time in milliseconds
-- ARGV[4]: unique log entry id
-- ARGV[5]: requested permits (added as separate entries)
-- Returns: 1 if allowed, 0 if denied

local key = KEYS[1]
local window = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local entry_id = ARGV[4]
local requested = tonumber(ARGV[5])

-- Remove expired entries
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

local count = redis.call('ZCARD', key)
if count + requested <= limit then
    for i = 1, requested do
        redis.call('ZADD', key, now, entry_id .. ':' .. i)
    end
    redis.call('PEXPIRE', key, window + 1000)
    return 1
end

return 0
```

- [ ] **Step 2: Write SlidingLogLimiterTest**

```java
package wangchongv8.myratelimiter.algorithm;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.redis.RedisOperations;

public class SlidingLogLimiterTest {

    private RedisOperations redisOps;
    private RateLimiter limiter;

    @Before
    public void setUp() {
        redisOps = mock(RedisOperations.class);
        RateLimiterConfig config = new RateLimiterConfig(5, 2, "rl:");
        limiter = new SlidingLogLimiter(config, redisOps);
    }

    @Test
    public void shouldAllowRequestWithinWindow() {
        when(redisOps.eval(anyString(), eq("rl:user:1"), anyList())).thenReturn(1L);
        assertTrue(limiter.tryAcquire("user:1"));
    }

    @Test
    public void shouldDenyWhenWindowExhausted() {
        when(redisOps.eval(anyString(), eq("rl:user:1"), anyList())).thenReturn(0L);
        assertFalse(limiter.tryAcquire("user:1"));
    }

    @Test
    public void shouldPassWindowSizeInMillis() {
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        when(redisOps.eval(anyString(), eq("rl:user:1"), captor.capture())).thenReturn(1L);

        limiter.tryAcquire("user:1", 2);

        List<String> args = captor.getValue();
        assertEquals("2000", args.get(0)); // 2 seconds in millis
        assertEquals("5", args.get(1));    // limit
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNullKey() {
        limiter.tryAcquire(null);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

```bash
mvn test -pl . -Dtest=SlidingLogLimiterTest
```

- [ ] **Step 4: Write SlidingLogLimiter** (same pattern as SlidingWindowLimiter, referencing sliding_log.lua)

```java
package wangchongv8.myratelimiter.algorithm;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.core.RateLimitExceededException;
import wangchongv8.myratelimiter.redis.RedisOperations;

public class SlidingLogLimiter implements RateLimiter {
    private static final String LUA_SCRIPT = loadScript("/lua/sliding_log.lua");

    private final RateLimiterConfig config;
    private final RedisOperations redisOps;

    public SlidingLogLimiter(RateLimiterConfig config, RedisOperations redisOps) {
        this.config = config;
        this.redisOps = redisOps;
    }

    @Override
    public boolean tryAcquire(String key) { return tryAcquire(key, 1); }

    @Override
    public boolean tryAcquire(String key, int permits) {
        if (key == null) throw new IllegalArgumentException("key must not be null");
        if (permits <= 0) throw new IllegalArgumentException("permits must be > 0");
        long now = System.currentTimeMillis();
        String redisKey = config.getRedisKeyPrefix() + key;
        List<String> args = Arrays.asList(
            String.valueOf(config.getIntervalSeconds() * 1000L),
            String.valueOf(config.getPermits()),
            String.valueOf(now),
            String.valueOf(System.nanoTime()),
            String.valueOf(permits)
        );
        Long result = redisOps.eval(LUA_SCRIPT, redisKey, args);
        return result != null && result == 1L;
    }

    @Override
    public void acquire(String key) { tryAcquire(key); }

    @Override
    public void acquire(String key, int permits) { tryAcquire(key, permits); }

    @Override
    public void acquire(String key, int permits, long timeout, TimeUnit unit) {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (!tryAcquire(key, permits)) {
            if (System.currentTimeMillis() >= deadline) {
                throw new RateLimitExceededException("Rate limit exceeded for key: " + key);
            }
            try { Thread.sleep(10); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RateLimitExceededException("Interrupted");
            }
        }
    }

    private static String loadScript(String path) {
        try (InputStream is = SlidingLogLimiter.class.getResourceAsStream(path);
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
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
mvn test -pl . -Dtest=SlidingLogLimiterTest
```

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/lua/sliding_log.lua \
        src/main/java/wangchongv8/myratelimiter/algorithm/SlidingLogLimiter.java \
        src/test/java/wangchongv8/myratelimiter/algorithm/SlidingLogLimiterTest.java
git commit -m "feat: add sliding log rate limiter"
```

---

### Task 9: RateLimiterBuilder + RateLimiterFactory

**Files:**
- Create: `src/main/java/wangchongv8/myratelimiter/builder/RateLimiterBuilder.java`
- Create: `src/main/java/wangchongv8/myratelimiter/builder/RateLimiterFactory.java`
- Create: `src/test/java/wangchongv8/myratelimiter/builder/RateLimiterBuilderTest.java`

- [ ] **Step 1: Write RateLimiterFactory**

```java
package wangchongv8.myratelimiter.builder;

import wangchongv8.myratelimiter.algorithm.*;
import wangchongv8.myratelimiter.core.Algorithm;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.redis.RedisOperations;

public class RateLimiterFactory {

    public static RateLimiter create(Algorithm algorithm, RateLimiterConfig config, RedisOperations redisOps) {
        if (algorithm == null) throw new IllegalArgumentException("algorithm must not be null");
        if (config == null) throw new IllegalArgumentException("config must not be null");
        if (redisOps == null) throw new IllegalArgumentException("redisOps must not be null");

        switch (algorithm) {
            case TOKEN_BUCKET:
                return new TokenBucketLimiter(config, redisOps);
            case LEAKY_BUCKET:
                return new LeakyBucketLimiter(config, redisOps);
            case FIXED_WINDOW:
                return new FixedWindowLimiter(config, redisOps);
            case SLIDING_WINDOW:
                return new SlidingWindowLimiter(config, redisOps);
            case SLIDING_LOG:
                return new SlidingLogLimiter(config, redisOps);
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        }
    }
}
```

- [ ] **Step 2: Write RateLimiterBuilder**

```java
package wangchongv8.myratelimiter.builder;

import wangchongv8.myratelimiter.core.Algorithm;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.redis.RedisOperations;

public class RateLimiterBuilder {
    private Algorithm algorithm;
    private int permits;
    private int intervalSeconds = 1;
    private String redisKeyPrefix = "rl:";
    private RedisOperations redisOps;

    public RateLimiterBuilder algorithm(Algorithm algorithm) {
        this.algorithm = algorithm;
        return this;
    }

    public RateLimiterBuilder permits(int permits) {
        this.permits = permits;
        return this;
    }

    public RateLimiterBuilder perSecond(int seconds) {
        this.intervalSeconds = seconds;
        return this;
    }

    public RateLimiterBuilder perMinute(int minutes) {
        this.intervalSeconds = minutes * 60;
        return this;
    }

    public RateLimiterBuilder redisKeyPrefix(String prefix) {
        this.redisKeyPrefix = prefix;
        return this;
    }

    public RateLimiterBuilder redisOps(RedisOperations redisOps) {
        this.redisOps = redisOps;
        return this;
    }

    public RateLimiter build() {
        if (algorithm == null) throw new IllegalArgumentException("algorithm is required");
        if (permits <= 0) throw new IllegalArgumentException("permits must be > 0, got: " + permits);
        if (redisOps == null) throw new IllegalArgumentException("redisOps is required");

        RateLimiterConfig config = new RateLimiterConfig(permits, intervalSeconds, redisKeyPrefix);
        return RateLimiterFactory.create(algorithm, config, redisOps);
    }
}
```

- [ ] **Step 3: Write failing test for Builder and Factory**

```java
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
        // verify prefix used via tryAcquire
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
```

- [ ] **Step 4: Run test to verify it fails**

```bash
mvn test -pl . -Dtest=RateLimiterBuilderTest
```

Expected: COMPILATION ERROR (Builder/Factory classes not found)

- [ ] **Step 5: Run test to verify it passes**

```bash
mvn test -pl . -Dtest=RateLimiterBuilderTest
```

Expected: All 11 tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/wangchongv8/myratelimiter/builder/ \
        src/test/java/wangchongv8/myratelimiter/builder/
git commit -m "feat: add RateLimiterBuilder and RateLimiterFactory"
```

---

### Task 10: RedissonRedisOps Adapter

**Files:**
- Create: `src/main/java/wangchongv8/myratelimiter/redis/RedissonRedisOps.java`
- Create: `src/test/java/wangchongv8/myratelimiter/redis/RedissonRedisOpsTest.java`

- [ ] **Step 1: Write RedissonRedisOps**

```java
package wangchongv8.myratelimiter.redis;

import java.util.Collections;
import java.util.List;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;

public class RedissonRedisOps implements RedisOperations {
    private final RedissonClient client;

    public RedissonRedisOps(RedissonClient client) {
        this.client = client;
    }

    @Override
    public Long eval(String script, String key, List<String> args) {
        return client.getScript().eval(
            RScript.Mode.READ_WRITE,
            script,
            RScript.ReturnType.INTEGER,
            Collections.singletonList(key),
            args.toArray()
        );
    }
}
```

- [ ] **Step 2: Write test**

```java
package wangchongv8.myratelimiter.redis;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;

public class RedissonRedisOpsTest {

    private RedissonClient client;
    private RScript rScript;
    private RedissonRedisOps ops;

    @Before
    public void setUp() {
        client = mock(RedissonClient.class);
        rScript = mock(RScript.class);
        when(client.getScript()).thenReturn(rScript);
        ops = new RedissonRedisOps(client);
    }

    @Test
    public void shouldEvalScript() {
        when(rScript.eval(any(RScript.Mode.class), anyString(),
                any(RScript.ReturnType.class), anyList(), any()))
            .thenReturn(1L);

        Long result = ops.eval("return 1", "mykey", Collections.singletonList("100"));

        assertEquals(Long.valueOf(1), result);
    }

    @Test
    public void shouldReturnZeroWhenLimited() {
        when(rScript.eval(any(RScript.Mode.class), anyString(),
                any(RScript.ReturnType.class), anyList(), any()))
            .thenReturn(0L);

        Long result = ops.eval("return 0", "mykey", Collections.singletonList("100"));

        assertEquals(Long.valueOf(0), result);
    }
}
```

- [ ] **Step 3: Run test to verify it fails (class not found), then passes**

```bash
mvn test -pl . -Dtest=RedissonRedisOpsTest
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/wangchongv8/myratelimiter/redis/RedissonRedisOps.java \
        src/test/java/wangchongv8/myratelimiter/redis/RedissonRedisOpsTest.java
git commit -m "feat: add RedissonRedisOps adapter"
```

---

### Task 11: Integration Tests

**Files:**
- Create: `src/test/java/wangchongv8/myratelimiter/integration/TokenBucketIT.java`
- Create: `src/test/java/wangchongv8/myratelimiter/integration/SlidingWindowIT.java`

- [ ] **Step 1: Write TokenBucketIT (requires local Redis)**

```java
package wangchongv8.myratelimiter.integration;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import redis.clients.jedis.JedisPool;
import wangchongv8.myratelimiter.core.Algorithm;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.builder.RateLimiterBuilder;
import wangchongv8.myratelimiter.redis.JedisRedisOps;

public class TokenBucketIT {

    private JedisPool jedisPool;
    private RateLimiter limiter;

    @Before
    public void setUp() {
        jedisPool = new JedisPool("localhost", 6379);
        limiter = new RateLimiterBuilder()
            .algorithm(Algorithm.TOKEN_BUCKET)
            .permits(5)
            .perSecond(1)
            .redisOps(new JedisRedisOps(jedisPool))
            .build();
    }

    @After
    public void tearDown() {
        jedisPool.close();
    }

    @Test
    public void shouldAllowWithinLimit() {
        for (int i = 0; i < 5; i++) {
            assertTrue("Request " + i + " should be allowed", limiter.tryAcquire("it:tb:1"));
        }
    }

    @Test
    public void shouldDenyOverLimit() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("it:tb:2");
        }
        assertFalse(limiter.tryAcquire("it:tb:2"));
    }

    @Test
    public void differentKeysShouldNotInterfere() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("it:tb:A");
        }
        // Different key should still be allowed
        assertTrue(limiter.tryAcquire("it:tb:B"));
    }
}
```

- [ ] **Step 2: Write SlidingWindowIT**

```java
package wangchongv8.myratelimiter.integration;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import redis.clients.jedis.JedisPool;
import wangchongv8.myratelimiter.core.Algorithm;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.builder.RateLimiterBuilder;
import wangchongv8.myratelimiter.redis.JedisRedisOps;

public class SlidingWindowIT {

    private JedisPool jedisPool;
    private RateLimiter limiter;

    @Before
    public void setUp() {
        jedisPool = new JedisPool("localhost", 6379);
        limiter = new RateLimiterBuilder()
            .algorithm(Algorithm.SLIDING_WINDOW)
            .permits(5)
            .perSecond(2)
            .redisOps(new JedisRedisOps(jedisPool))
            .build();
    }

    @After
    public void tearDown() {
        jedisPool.close();
    }

    @Test
    public void shouldAllowWithinWindow() {
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("it:sw:1"));
        }
    }

    @Test
    public void shouldDenyOverWindow() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("it:sw:2");
        }
        assertFalse(limiter.tryAcquire("it:sw:2"));
    }

    @Test
    public void shouldRecoverAfterWindowPasses() throws InterruptedException {
        String key = "it:sw:3";
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire(key);
        }
        assertFalse(limiter.tryAcquire(key));

        // Wait for window to pass
        Thread.sleep(2100);

        assertTrue(limiter.tryAcquire(key));
    }
}
```

- [ ] **Step 3: Run integration tests (requires Redis)**

```bash
mvn verify -pl .
```

**Prerequisite:** Redis running on localhost:6379

- [ ] **Step 4: Commit**

```bash
git add src/test/java/wangchongv8/myratelimiter/integration/
git commit -m "test: add integration tests for token bucket and sliding window"
```

---

### Task 12: Refactor Duplicated acquire/loadScript Logic

**Files:**
- Create: `src/main/java/wangchongv8/myratelimiter/algorithm/AbstractRateLimiter.java`
- Modify: All 5 algorithm classes to extend `AbstractRateLimiter`

- [ ] **Step 1: Extract AbstractRateLimiter**

```java
package wangchongv8.myratelimiter.algorithm;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import wangchongv8.myratelimiter.core.RateLimiter;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.core.RateLimitExceededException;
import wangchongv8.myratelimiter.redis.RedisOperations;

public abstract class AbstractRateLimiter implements RateLimiter {
    protected final RateLimiterConfig config;
    protected final RedisOperations redisOps;

    protected AbstractRateLimiter(RateLimiterConfig config, RedisOperations redisOps) {
        this.config = config;
        this.redisOps = redisOps;
    }

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

    protected void validate(String key, int permits) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be > 0");
        }
    }

    @Override
    public boolean tryAcquire(String key) {
        return tryAcquire(key, 1);
    }

    @Override
    public void acquire(String key) {
        acquire(key, 1, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    @Override
    public void acquire(String key, int permits) {
        acquire(key, permits, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    @Override
    public void acquire(String key, int permits, long timeout, TimeUnit unit) {
        validate(key, permits);
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (true) {
            if (tryAcquire(key, permits)) {
                return;
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new RateLimitExceededException(
                    "Rate limit exceeded for key: " + key);
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RateLimitExceededException("Interrupted while waiting for rate limit");
            }
        }
    }
}
```

- [ ] **Step 2: Simplify TokenBucketLimiter to extend AbstractRateLimiter**

```java
package wangchongv8.myratelimiter.algorithm;

import java.util.Arrays;
import java.util.List;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.redis.RedisOperations;

public class TokenBucketLimiter extends AbstractRateLimiter {
    private static final String LUA_SCRIPT = loadScript("/lua/token_bucket.lua");
    private final double rate;

    public TokenBucketLimiter(RateLimiterConfig config, RedisOperations redisOps) {
        super(config, redisOps);
        this.rate = (double) config.getPermits() / config.getIntervalSeconds();
    }

    @Override
    public boolean tryAcquire(String key, int permits) {
        validate(key, permits);
        String redisKey = config.getRedisKeyPrefix() + key;
        List<String> args = Arrays.asList(
            String.valueOf(config.getPermits()),
            String.valueOf(rate),
            String.valueOf(permits),
            String.valueOf(System.currentTimeMillis())
        );
        Long result = redisOps.eval(LUA_SCRIPT, redisKey, args);
        return result != null && result == 1L;
    }
}
```

- [ ] **Step 3: Simplify FixedWindowLimiter**

```java
package wangchongv8.myratelimiter.algorithm;

import java.util.Arrays;
import java.util.List;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.redis.RedisOperations;

public class FixedWindowLimiter extends AbstractRateLimiter {
    private static final String LUA_SCRIPT = loadScript("/lua/fixed_window.lua");

    public FixedWindowLimiter(RateLimiterConfig config, RedisOperations redisOps) {
        super(config, redisOps);
    }

    @Override
    public boolean tryAcquire(String key, int permits) {
        validate(key, permits);
        String redisKey = config.getRedisKeyPrefix() + key;
        List<String> args = Arrays.asList(
            String.valueOf(config.getIntervalSeconds()),
            String.valueOf(config.getPermits()),
            String.valueOf(permits)
        );
        Long result = redisOps.eval(LUA_SCRIPT, redisKey, args);
        return result != null && result == 1L;
    }
}
```

- [ ] **Step 4a: Simplify LeakyBucketLimiter**

```java
package wangchongv8.myratelimiter.algorithm;

import java.util.Arrays;
import java.util.List;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.redis.RedisOperations;

public class LeakyBucketLimiter extends AbstractRateLimiter {
    private static final String LUA_SCRIPT = loadScript("/lua/leaky_bucket.lua");
    private final double rate;

    public LeakyBucketLimiter(RateLimiterConfig config, RedisOperations redisOps) {
        super(config, redisOps);
        this.rate = (double) config.getPermits() / config.getIntervalSeconds();
    }

    @Override
    public boolean tryAcquire(String key, int permits) {
        validate(key, permits);
        String redisKey = config.getRedisKeyPrefix() + key;
        List<String> args = Arrays.asList(
            String.valueOf(config.getPermits()),
            String.valueOf(rate),
            String.valueOf(permits),
            String.valueOf(System.currentTimeMillis())
        );
        Long result = redisOps.eval(LUA_SCRIPT, redisKey, args);
        return result != null && result == 1L;
    }
}
```

- [ ] **Step 4b: Simplify SlidingWindowLimiter**

```java
package wangchongv8.myratelimiter.algorithm;

import java.util.Arrays;
import java.util.List;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.redis.RedisOperations;

public class SlidingWindowLimiter extends AbstractRateLimiter {
    private static final String LUA_SCRIPT = loadScript("/lua/sliding_window.lua");

    public SlidingWindowLimiter(RateLimiterConfig config, RedisOperations redisOps) {
        super(config, redisOps);
    }

    @Override
    public boolean tryAcquire(String key, int permits) {
        validate(key, permits);
        long now = System.currentTimeMillis();
        String redisKey = config.getRedisKeyPrefix() + key;
        List<String> args = Arrays.asList(
            String.valueOf(config.getIntervalSeconds() * 1000L),
            String.valueOf(config.getPermits()),
            String.valueOf(now),
            String.valueOf(System.nanoTime()),
            String.valueOf(permits)
        );
        Long result = redisOps.eval(LUA_SCRIPT, redisKey, args);
        return result != null && result == 1L;
    }
}
```

- [ ] **Step 4c: Simplify SlidingLogLimiter**

```java
package wangchongv8.myratelimiter.algorithm;

import java.util.Arrays;
import java.util.List;
import wangchongv8.myratelimiter.core.RateLimiterConfig;
import wangchongv8.myratelimiter.redis.RedisOperations;

public class SlidingLogLimiter extends AbstractRateLimiter {
    private static final String LUA_SCRIPT = loadScript("/lua/sliding_log.lua");

    public SlidingLogLimiter(RateLimiterConfig config, RedisOperations redisOps) {
        super(config, redisOps);
    }

    @Override
    public boolean tryAcquire(String key, int permits) {
        validate(key, permits);
        long now = System.currentTimeMillis();
        String redisKey = config.getRedisKeyPrefix() + key;
        List<String> args = Arrays.asList(
            String.valueOf(config.getIntervalSeconds() * 1000L),
            String.valueOf(config.getPermits()),
            String.valueOf(now),
            String.valueOf(System.nanoTime()),
            String.valueOf(permits)
        );
        Long result = redisOps.eval(LUA_SCRIPT, redisKey, args);
        return result != null && result == 1L;
    }
}
```

- [ ] **Step 5: Run all tests to ensure no regression**

```bash
mvn test
```

Expected: All unit tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/wangchongv8/myratelimiter/algorithm/
git commit -m "refactor: extract AbstractRateLimiter to eliminate duplicate code"
```

---

### Task 13: Final Verification

- [ ] **Step 1: Run full test suite**

```bash
mvn clean verify
```

Expected: All unit tests PASS, integration tests run if Redis is available

- [ ] **Step 2: Verify packaging**

```bash
mvn package -DskipTests
```

Expected: `target/my-ratelimiter-1.0.0-SNAPSHOT.jar` created, only contains `wangchongv8.myratelimiter` classes + Lua resources

- [ ] **Step 3: Final commit**

```bash
git add -A
git commit -m "chore: finalize project structure"
```
