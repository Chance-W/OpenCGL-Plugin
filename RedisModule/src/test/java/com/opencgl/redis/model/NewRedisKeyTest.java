package com.opencgl.redis.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static com.opencgl.redis.model.RedisKeyInfo.KeyType.*;

class NewRedisKeyTest {
    @Test void stringPreservesWhitespaceAndEmptyValue() {
        assertEquals(List.of("SET", "0", " a\n b "), NewRedisKey.of("k", STRING, "", " a\n b ", List.of(), "*").arguments());
        assertEquals(List.of("SET", "0", ""), NewRedisKey.of("k", STRING, "0", "", List.of(), "*").arguments());
    }
    @Test void emitsTypedArgumentsWithoutSplittingValues() {
        var rows = List.of(new NewRedisKey.Row("field", "a\nb"));
        assertEquals(List.of("HSET", "60", "field", "a\nb"), NewRedisKey.of("k", HASH, "60", "", rows, "*").arguments());
        assertEquals(List.of("RPUSH", "0", "field"), NewRedisKey.of("k", LIST, "", "", rows, "*").arguments());
        assertEquals(List.of("SADD", "0", "field"), NewRedisKey.of("k", SET, "", "", rows, "*").arguments());
        assertEquals(List.of("ZADD", "0", "1.5", "member"), NewRedisKey.of("k", ZSET, "", "", List.of(new NewRedisKey.Row("member", "1.5")), "*").arguments());
        assertEquals(List.of("XADD", "0", "*", "field", "a\nb"), NewRedisKey.of("k", STREAM, "", "", rows, "*").arguments());
    }
    @Test void rejectsInvalidOrMissingInputs() {
        assertThrows(IllegalArgumentException.class, () -> NewRedisKey.of("", STRING, "", "", List.of(), "*"));
        for (String ttl : List.of("-1", "abc", "1.5", "9223372036854775807"))
            assertThrows(IllegalArgumentException.class, () -> NewRedisKey.of("k", STRING, ttl, "", List.of(), "*"));
        for (var type : List.of(HASH, LIST, SET, ZSET, STREAM))
            assertThrows(IllegalArgumentException.class, () -> NewRedisKey.of("k", type, "", "", List.of(), "*"));
        for (String score : List.of("", "NaN", "Infinity", "bad"))
            assertThrows(IllegalArgumentException.class, () -> NewRedisKey.of("k", ZSET, "", "", List.of(new NewRedisKey.Row("m", score)), "*"));
        assertThrows(IllegalArgumentException.class, () -> NewRedisKey.of("k", STREAM, "", "", List.of(new NewRedisKey.Row("f", "v")), "0-0"));
        assertThrows(IllegalArgumentException.class, () -> NewRedisKey.of("k", NONE, "", "", List.of(), "*"));
    }
    @Test void duplicateFieldsAreRejectedRatherThanSilentlyLost() {
        assertThrows(IllegalArgumentException.class, () -> NewRedisKey.of("k", HASH, "", "", List.of(new NewRedisKey.Row("f", "1"), new NewRedisKey.Row("f", "2")), "*"));
    }
}
