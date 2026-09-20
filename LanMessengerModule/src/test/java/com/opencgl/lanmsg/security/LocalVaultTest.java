package com.opencgl.lanmsg.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LocalVaultTest {
    @TempDir Path directory;
    static final class MemoryStore implements SecretStore {
        byte[] key;
        int creates;
        @Override public Optional<byte[]> read() { return key == null ? Optional.empty() : Optional.of(key.clone()); }
        @Override public void create(byte[] bytes) throws IOException {
            if (key != null) throw new IOException("Already exists");
            key = bytes.clone(); creates++;
        }
    }

    @Test void encryptedRecordsSurviveReopenWithoutExposingNamesOrContent() throws Exception {
        var store = new MemoryStore();
        Path root = directory.resolve("vault");
        byte[] text = "机密聊天中文📎\n第二行".getBytes(StandardCharsets.UTF_8);
        try (var vault = LocalVault.open(root, store)) {
            vault.write("history", "../中文.txt", text);
            assertTrue(vault.read("history", "missing").isEmpty());
            vault.write("identity", "../中文.txt", new byte[]{9});
        }
        try (var vault = LocalVault.open(root, store)) {
            assertArrayEquals(text, vault.read("history", "../中文.txt").orElseThrow());
            assertArrayEquals(new byte[]{9}, vault.read("identity", "../中文.txt").orElseThrow());
        }
        assertEquals(1, store.creates);
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                assertFalse(file.getFileName().toString().contains("中文"));
                assertFalse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).contains("机密聊天"));
                assertFalse(Arrays.equals(store.key, Files.readAllBytes(file)));
            }
        }
        assertFalse(Files.exists(directory.resolve("中文.txt")));
    }

    @Test void missingKeyOrWrongKeyDoesNotReplaceExistingEncryptedData() throws Exception {
        var store = new MemoryStore(); Path root = directory.resolve("vault");
        try (var vault = LocalVault.open(root, store)) { vault.write("history", "a", new byte[]{1}); }
        byte[] goodKey = store.key.clone();
        store.key = null;
        assertThrows(IOException.class, () -> LocalVault.open(root, store));
        assertEquals(1, store.creates);
        store.key = new byte[32];
        assertThrows(IOException.class, () -> LocalVault.open(root, store));
        store.key = goodKey;
        try (var vault = LocalVault.open(root, store)) {
            assertArrayEquals(new byte[]{1}, vault.read("history", "a").orElseThrow());
        }
    }

    @Test void corruptMarkerAndUnmarkedDataFailClosed() throws Exception {
        var store = new MemoryStore(); Path root = directory.resolve("vault");
        try (var ignored = LocalVault.open(root, store)) { }
        byte[] marker = Files.readAllBytes(root.resolve("vault.check"));
        marker[marker.length - 1] ^= 1; Files.write(root.resolve("vault.check"), marker);
        assertThrows(IOException.class, () -> LocalVault.open(root, store));
        assertEquals(1, store.creates);
        Path unmarked = directory.resolve("unmarked"); Files.createDirectory(unmarked);
        Files.write(unmarked.resolve("old-record"), new byte[]{1});
        var empty = new MemoryStore();
        assertThrows(IOException.class, () -> LocalVault.open(unmarked, empty));
        assertEquals(0, empty.creates);
    }

    @Test void retainsOldRecordOnRejectedWriteAndRefusesTamperedRecord() throws Exception {
        var store = new MemoryStore(); Path root = directory.resolve("vault");
        try (var vault = LocalVault.open(root, store)) {
            vault.write("history", "a", new byte[]{7});
            assertThrows(IllegalArgumentException.class,
                    () -> vault.write("history", "a", new byte[LocalCipher.MAX_PLAINTEXT_BYTES + 1]));
            assertArrayEquals(new byte[]{7}, vault.read("history", "a").orElseThrow());
            Path record;
            try (var files = Files.list(root)) { record = files.filter(p -> p.toString().endsWith(".sealed")).findFirst().orElseThrow(); }
            byte[] changed = Files.readAllBytes(record); changed[changed.length - 1] ^= 1;
            Files.write(record, changed);
            assertThrows(IOException.class, () -> vault.read("history", "a"));
        }
    }

    @Test void refusesConcurrentOpenAndDoesNotUseClosedKeyMaterial() throws Exception {
        var store = new MemoryStore(); Path root = directory.resolve("vault");
        var vault = LocalVault.open(root, store);
        assertThrows(IOException.class, () -> LocalVault.open(root, store));
        vault.close(); vault.close();
        assertThrows(IllegalStateException.class, () -> vault.write("p", "id", new byte[0]));
        try (var reopened = LocalVault.open(root, store)) { reopened.write("p", "id", new byte[0]); }
    }

    @Test void refusesSymlinkDirectoryMarkerAndRecord() throws Exception {
        Path real = directory.resolve("real"); Files.createDirectory(real);
        Path linked = directory.resolve("linked"); Files.createSymbolicLink(linked, real);
        var store = new MemoryStore();
        assertThrows(IOException.class, () -> LocalVault.open(linked, store));
        Path root = directory.resolve("vault");
        try (var vault = LocalVault.open(root, store)) {
            vault.write("p", "id", new byte[]{7});
            Path record;
            try (var files = Files.list(root)) { record = files.filter(p -> p.toString().endsWith(".sealed")).findFirst().orElseThrow(); }
            Path target = directory.resolve("external"); Files.write(target, new byte[]{9});
            Files.delete(record); Files.createSymbolicLink(record, target);
            assertThrows(IOException.class, () -> vault.read("p", "id"));
            assertThrows(IOException.class, () -> vault.write("p", "id", new byte[]{1}));
            assertArrayEquals(new byte[]{9}, Files.readAllBytes(target));
        }
        Files.delete(root.resolve("vault.check"));
        Files.createSymbolicLink(root.resolve("vault.check"), directory.resolve("external"));
        assertThrows(IOException.class, () -> LocalVault.open(root, store));
    }
}
