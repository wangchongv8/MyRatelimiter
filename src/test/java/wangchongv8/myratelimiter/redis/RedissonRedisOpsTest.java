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
