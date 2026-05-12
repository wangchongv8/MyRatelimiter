package wangchongv8.myratelimiter.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisNoScriptException;

/**
 * 基于 Jedis 连接池的 {@link RedisOperations} 实现。
 *
 * <p>使用 SHA-1 摘要做脚本缓存优化：首次调用 {@code evalsha}，Redis 返回 NOSCRIPT 时
 * 自动降级为 {@code eval} 并加载脚本。后续调用直接从脚本缓存执行。
 *
 * <pre>{@code
 * JedisPool pool = new JedisPool("localhost", 6379);
 * RedisOperations ops = new JedisRedisOps(pool);
 * }</pre>
 *
 * <p>连接生命周期通过 try-with-resources 管理，每次调用从池中获取连接并在执行后归还。
 */
public class JedisRedisOps implements RedisOperations {
    private final JedisPool jedisPool;

    /**
     * @param jedisPool Jedis 连接池
     */
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

    /** 计算字符串的 SHA-1 十六进制摘要 */
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
