package com.opencgl.lanmsg.security;

import com.sun.jna.platform.win32.Crypt32Util;
import com.sun.jna.platform.win32.WinCrypt;
import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Optional;

/** The only on-disk key material is a current-user DPAPI-protected blob. */
final class WindowsDpapiStore implements SecretStore {
    interface Protection {
        byte[] protect(byte[] data);
        byte[] unprotect(byte[] data);
    }
    private final Path file;
    private final Protection protection;
    WindowsDpapiStore(Path file) {
        this(file, new Protection() {
            // No CRYPTPROTECT_LOCAL_MACHINE: another Windows user must not be able to unwrap it.
            public byte[] protect(byte[] data) { return Crypt32Util.cryptProtectData(data, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN); }
            public byte[] unprotect(byte[] data) { return Crypt32Util.cryptUnprotectData(data, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN); }
        });
    }
    WindowsDpapiStore(Path file, Protection protection) { this.file = file; this.protection = protection; }

    @Override public synchronized Optional<byte[]> read() throws IOException {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        byte[] blob = PrivateFiles.read(file, 16384);
        byte[] key = null;
        try {
            key = protection.unprotect(blob);
            if (key == null || key.length != 32) throw new IOException("Invalid DPAPI vault key");
            return Optional.of(key.clone());
        } catch (RuntimeException | LinkageError e) { throw new IOException("Windows DPAPI could not unlock this user's key"); }
        finally { Arrays.fill(blob, (byte) 0); if (key != null) Arrays.fill(key, (byte) 0); }
    }

    @Override public synchronized void create(byte[] secret) throws IOException {
        if (secret == null || secret.length != 32) throw new IOException("A 256-bit key is required");
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("DPAPI key already exists; not replacing it");
        byte[] copy = secret.clone(), blob = null;
        try {
            blob = protection.protect(copy);
            if (blob == null || blob.length == 0 || blob.length > 16384) throw new IOException("Invalid DPAPI response");
            PrivateFiles.atomicWrite(file, blob);
        } catch (RuntimeException | LinkageError e) { throw new IOException("Windows DPAPI could not protect the key"); }
        finally { Arrays.fill(copy, (byte) 0); if (blob != null) Arrays.fill(blob, (byte) 0); }
    }
}
