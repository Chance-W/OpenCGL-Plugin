package com.opencgl.lanmsg.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SystemSecretStoreTest {
    @TempDir Path directory;
    private static byte[] key() { byte[] bytes = new byte[32]; Arrays.fill(bytes, (byte) 37); return bytes; }

    @Test void macStoreDistinguishesNotFoundFromDeniedAndDoesNotOverwrite() throws Exception {
        final class Backend implements MacKeychainStore.Backend {
            int status = -25300; byte[] stored;
            public MacKeychainStore.Result find(byte[] service, byte[] account) {
                assertEquals("OpenCGL.LanMessenger.v1", new String(service, java.nio.charset.StandardCharsets.UTF_8));
                assertEquals("profile", new String(account, java.nio.charset.StandardCharsets.UTF_8));
                return new MacKeychainStore.Result(status, stored == null ? null : stored.clone());
            }
            public int add(byte[] service, byte[] account, byte[] secret) {
                if (stored != null) return -25299;
                stored = secret.clone(); status = 0; return 0;
            }
        }
        var backend = new Backend(); var store = new MacKeychainStore("profile", backend);
        assertTrue(store.read().isEmpty());
        store.create(key()); assertArrayEquals(key(), store.read().orElseThrow());
        assertThrows(IOException.class, () -> store.create(new byte[32]));
        assertArrayEquals(key(), store.read().orElseThrow());
        backend.status = -25308;
        assertThrows(IOException.class, store::read);
        backend.status = 0; backend.stored = new byte[3];
        assertThrows(IOException.class, store::read);
    }

    @Test void windowsPersistsOnlyProtectedBlobAndNeverReplacesExistingKey() throws Exception {
        // Fake only OS DPAPI: test actual filesystem and adapter behavior, not Windows cryptography.
        var calls = new ArrayList<byte[]>();
        WindowsDpapiStore.Protection protection = new WindowsDpapiStore.Protection() {
            public byte[] protect(byte[] data) { calls.add(data.clone()); return new byte[]{5, 4, 3}; }
            public byte[] unprotect(byte[] data) {
                assertArrayEquals(new byte[]{5, 4, 3}, data); return key();
            }
        };
        Path blob = directory.resolve("master.dpapi");
        var store = new WindowsDpapiStore(blob, protection);
        assertTrue(store.read().isEmpty()); store.create(key());
        assertArrayEquals(key(), calls.get(0));
        assertArrayEquals(new byte[]{5, 4, 3}, Files.readAllBytes(blob));
        assertArrayEquals(key(), new WindowsDpapiStore(blob, protection).read().orElseThrow());
        assertThrows(IOException.class, () -> store.create(new byte[32]));
        Files.write(blob, new byte[20_000]);
        assertThrows(IOException.class, store::read);
    }

    @Test void linuxNeverPutsSecretInArgumentsAndRejectsUnavailableService() throws Exception {
        var commands = new ArrayList<List<String>>();
        byte[][] stored = {null};
        LinuxSecretStore.Runner runner = (args, input) -> {
            commands.add(List.copyOf(args));
            if (args.get(0).equals("lookup"))
                return new SecretToolRunner.Result(stored[0] == null ? 1 : 0, stored[0] == null ? new byte[0] : stored[0].clone(), false);
            if (args.get(0).equals("search"))
                return new SecretToolRunner.Result(0, stored[0] == null ? new byte[0] : new byte[]{91, 93}, false);
            assertEquals("store", args.get(0));
            assertArrayEquals(Base64.getEncoder().encode(key()), input);
            stored[0] = input.clone(); return new SecretToolRunner.Result(0, new byte[0], false);
        };
        var store = new LinuxSecretStore("profile", runner);
        assertTrue(store.read().isEmpty()); store.create(key());
        assertArrayEquals(key(), store.read().orElseThrow());
        assertThrows(IOException.class, () -> store.create(new byte[32]));
        assertFalse(commands.toString().contains(Base64.getEncoder().encodeToString(key())));
        assertTrue(commands.stream().allMatch(c -> c.contains("OpenCGL.LanMessenger.v1") && c.contains("profile")));
        var denied = new LinuxSecretStore("profile", (args, input) -> new SecretToolRunner.Result(1, new byte[0], true));
        assertThrows(IOException.class, denied::read);
        var corrupt = new LinuxSecretStore("profile", (args, input) -> new SecretToolRunner.Result(0, new byte[]{1, 2, 3}, false));
        assertThrows(IOException.class, corrupt::read);
    }

    @Test void cancelledLinuxUnlockIsNotMistakenForMissingKey() throws Exception {
        var calls = new ArrayList<String>();
        var locked = new LinuxSecretStore("profile", (args, input) -> {
            calls.add(args.get(0));
            if (args.get(0).equals("lookup")) return new SecretToolRunner.Result(1, new byte[0], false);
            if (args.get(0).equals("search")) return new SecretToolRunner.Result(0, "[/1]\nlabel=existing".getBytes(), true);
            return new SecretToolRunner.Result(0, new byte[0], false);
        });
        assertThrows(IOException.class, locked::read);
        assertThrows(IOException.class, () -> locked.create(key()));
        assertFalse(calls.contains("store"));
    }
}
