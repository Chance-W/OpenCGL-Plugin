package com.opencgl.lanmsg.security;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class LocalCipherTest {
    private static byte[] key() { byte[] key = new byte[32]; Arrays.fill(key, (byte) 42); return key; }

    @Test void roundTripsChineseEmojiAndBinaryWithoutReusingNonce() throws Exception {
        try (var cipher = new LocalCipher(key())) {
            byte[] text = "中文文件📎\n第二行\u0000".getBytes(StandardCharsets.UTF_8);
            byte[] first = cipher.seal("message", "消息-1", text);
            byte[] second = cipher.seal("message", "消息-1", text);
            assertArrayEquals(text, cipher.open("message", "消息-1", first));
            assertArrayEquals(text, cipher.open("message", "消息-1", second));
            assertFalse(Arrays.equals(first, second));
            assertFalse(new String(first, StandardCharsets.UTF_8).contains("中文文件"));
            byte[] empty = cipher.seal("image", "empty", new byte[0]);
            assertArrayEquals(new byte[0], cipher.open("image", "empty", empty));
        }
    }

    @Test void rejectsWrongKeyPurposeRecordAndEveryChangedEnvelopeByte() throws Exception {
        try (var cipher = new LocalCipher(key()); var wrong = new LocalCipher(new byte[32])) {
            byte[] sealed = cipher.seal("message", "id-1", new byte[]{1, 2, 3});
            assertThrows(GeneralSecurityException.class, () -> wrong.open("message", "id-1", sealed));
            assertThrows(GeneralSecurityException.class, () -> cipher.open("identity", "id-1", sealed));
            assertThrows(GeneralSecurityException.class, () -> cipher.open("message", "id-2", sealed));
            for (int i = 0; i < sealed.length; i++) {
                byte[] changed = sealed.clone(); changed[i] ^= 1;
                assertThrows(GeneralSecurityException.class, () -> cipher.open("message", "id-1", changed));
            }
            assertThrows(GeneralSecurityException.class,
                    () -> cipher.open("message", "id-1", Arrays.copyOf(sealed, sealed.length - 1)));
        }
    }

    @Test void contextIsUnambiguousAndCallerCannotMutateOwnedKey() throws Exception {
        byte[] raw = key();
        try (var cipher = new LocalCipher(raw)) {
            byte[] sealed = cipher.seal("ab", "c", new byte[]{7});
            Arrays.fill(raw, (byte) 0);
            assertArrayEquals(new byte[]{7}, cipher.open("ab", "c", sealed));
            assertThrows(GeneralSecurityException.class, () -> cipher.open("a", "bc", sealed));
        }
    }

    @Test void boundsInputAndRejectsUseAfterClose() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new LocalCipher(new byte[16]));
        var cipher = new LocalCipher(key());
        assertThrows(GeneralSecurityException.class, () -> cipher.open("message", "id", new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> cipher.seal("", "id", new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> cipher.seal("p", "x".repeat(4097), new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> cipher.seal("p", "id", new byte[16 * 1024 * 1024 + 1]));
        byte[] sealed = cipher.seal("p", "id", new byte[0]);
        cipher.close(); cipher.close();
        assertThrows(IllegalStateException.class, () -> cipher.seal("p", "id", new byte[0]));
        assertThrows(IllegalStateException.class, () -> cipher.open("p", "id", sealed));
    }
}
