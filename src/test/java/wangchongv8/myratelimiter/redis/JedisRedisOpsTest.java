package wangchongv8.myratelimiter.redis;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

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
