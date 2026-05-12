package wangchongv8.myratelimiter.redis;

import java.util.List;

/**
 * Redis 操作 SPI（Service Provider Interface）。
 *
 * <p>抽象 Redis 客户端差异，内置实现了 {@link JedisRedisOps} 和 {@link RedissonRedisOps}。
 * 如需支持其他 Redis 客户端（如 Lettuce），只需实现此接口即可。
 *
 * <pre>{@code
 * // 自定义 Lettuce 适配
 * public class LettuceRedisOps implements RedisOperations {
 *     private final StatefulRedisConnection<String, String> conn;
 *
 *     public LettuceRedisOps(StatefulRedisConnection<String, String> conn) {
 *         this.conn = conn;
 *     }
 *
 *     public Long eval(String script, String key, List<String> args) {
 *         // 调用 Lettuce 的 eval 方法
 *     }
 * }
 * }</pre>
 */
public interface RedisOperations {

    /**
     * 执行 Lua 脚本并返回结果。
     *
     * @param script Lua 脚本完整内容
     * @param key    脚本中的 KEYS[1]
     * @param args   脚本中的 ARGV 参数列表
     * @return Lua 脚本返回值，通常 1 表示放行、0 表示限流
     */
    Long eval(String script, String key, List<String> args);
}
