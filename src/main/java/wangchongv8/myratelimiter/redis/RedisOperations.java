package wangchongv8.myratelimiter.redis;

import java.util.List;

public interface RedisOperations {
    Long eval(String script, String key, List<String> args);
}
