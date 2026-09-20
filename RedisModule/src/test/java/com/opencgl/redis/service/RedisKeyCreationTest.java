package com.opencgl.redis.service;

import com.opencgl.redis.model.*;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RedisKeyCreationTest {
    // Only replace the external Redis transport; exercise the actual manager contract.
    static class Transport extends Jedis {
        Object reply = 1L;
        List<String> keys, args;
        @Override public String type(String key) { return "stream"; }
        @Override public Long ttl(String key) { return -1L; }
        @Override public Object eval(String script, List<String> keys, List<String> args) {
            this.keys = keys; this.args = args;
            if (reply instanceof RuntimeException e) throw e;
            return reply;
        }
    }
    static RedisConnectionManager manager(Transport transport) throws Exception {
        var m = new RedisConnectionManager();
        var f = RedisConnectionManager.class.getDeclaredField("jedis"); f.setAccessible(true); f.set(m, transport);
        f = RedisConnectionManager.class.getDeclaredField("connected"); f.setAccessible(true); f.set(m, true);
        return m;
    }
    @Test void routesExactKeyAndValuesAndDistinguishesDuplicate() throws Exception {
        var transport = new Transport(); var m = manager(transport);
        var request = NewRedisKey.of("a:'\n", RedisKeyInfo.KeyType.STRING, "30", " b\n c ", List.of(), "*");
        assertTrue(m.createKey(request));
        assertEquals(List.of("a:'\n"), transport.keys);
        assertEquals(List.of("SET", "30", " b\n c "), transport.args);
        transport.reply = 0L;
        assertFalse(m.createKey(request));
    }
    @Test void connectionAndServerFailuresAreNotReportedAsSuccess() throws Exception {
        var request = NewRedisKey.of("k", RedisKeyInfo.KeyType.STRING, "", "", List.of(), "*");
        assertThrows(IllegalStateException.class, () -> new RedisConnectionManager().createKey(request));
        var transport = new Transport(); var m = manager(transport);
        transport.reply = new IllegalStateException("NOPERM");
        assertThrows(IllegalStateException.class, () -> m.createKey(request));
    }
    @Test void streamEntriesAreDecodedForDisplay() throws Exception {
        var transport = new Transport(); var m = manager(transport);
        transport.reply = List.of(List.of("123-0", List.of("f", "value\nline")));
        assertEquals(Map.of("123-0", Map.of("f", "value\nline")), m.getValue("stream"));
    }
}
