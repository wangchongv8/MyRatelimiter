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
