package com.opencgl.lanmsg.security;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/** Login Keychain generic-password entry; no command-line secret exposure. */
final class MacKeychainStore implements SecretStore {
    private static final byte[] SERVICE = "OpenCGL.LanMessenger.v1".getBytes(StandardCharsets.UTF_8);
    private static final int NOT_FOUND = -25300;
    private final byte[] account;
    private final Backend backend;

    interface Backend {
        Result find(byte[] service, byte[] account) throws IOException;
        int add(byte[] service, byte[] account, byte[] secret) throws IOException;
    }
    record Result(int status, byte[] secret) { }

    MacKeychainStore(String account) { this(account, new NativeBackend()); }
    MacKeychainStore(String account, Backend backend) {
        this.account = account.getBytes(StandardCharsets.UTF_8); this.backend = backend;
    }

    @Override public synchronized Optional<byte[]> read() throws IOException {
        Result result;
        try { result = backend.find(SERVICE, account); }
        catch (RuntimeException | LinkageError e) { throw new IOException("macOS Keychain is unavailable"); }
        try {
            if (result.status() == NOT_FOUND) return Optional.empty();
            if (result.status() != 0) throw new IOException("macOS Keychain access failed (status " + result.status() + ")");
            if (result.secret() == null || result.secret().length != 32) throw new IOException("Invalid key in macOS Keychain");
            return Optional.of(result.secret().clone());
        } finally { if (result.secret() != null) Arrays.fill(result.secret(), (byte) 0); }
    }

    @Override public synchronized void create(byte[] secret) throws IOException {
        if (secret == null || secret.length != 32) throw new IOException("A 256-bit key is required");
        byte[] existing = read().orElse(null);
        if (existing != null) { Arrays.fill(existing, (byte) 0); throw new IOException("Keychain entry already exists; not replacing it"); }
        byte[] copy = secret.clone();
        try {
            int status = backend.add(SERVICE, account, copy);
            if (status != 0) throw new IOException("Cannot create Keychain entry (status " + status + ")");
        } catch (RuntimeException | LinkageError e) { throw new IOException("macOS Keychain is unavailable"); }
        finally { Arrays.fill(copy, (byte) 0); }
    }

    // These legacy generic-password APIs remain supported by the macOS login Keychain.
    // Lazy loading keeps other OSes and injected tests independent of the Security framework.
    interface Security extends Library {
        int SecKeychainFindGenericPassword(Pointer keychain, int serviceLength, byte[] service, int accountLength,
                byte[] account, IntByReference length, PointerByReference data, Pointer item);
        int SecKeychainAddGenericPassword(Pointer keychain, int serviceLength, byte[] service, int accountLength,
                byte[] account, int length, Pointer data, Pointer item);
        int SecKeychainItemFreeContent(Pointer attributes, Pointer data);
    }
    private static final class NativeApi {
        static final Security SECURITY = Native.load("/System/Library/Frameworks/Security.framework/Security", Security.class);
    }
    private static final class NativeBackend implements Backend {
        @Override public Result find(byte[] service, byte[] account) throws IOException {
            var length = new IntByReference(); var data = new PointerByReference();
            var api = NativeApi.SECURITY;
            int status = api.SecKeychainFindGenericPassword(null, service.length, service, account.length, account, length, data, null);
            Pointer pointer = data.getValue();
            try {
                if (status != 0) return new Result(status, null);
                if (length.getValue() != 32 || pointer == null) throw new IOException("Invalid Keychain key length");
                return new Result(0, pointer.getByteArray(0, 32));
            } finally {
                if (pointer != null) {
                    if (length.getValue() == 32) pointer.clear(32);
                    api.SecKeychainItemFreeContent(null, pointer);
                }
            }
        }
        @Override public int add(byte[] service, byte[] account, byte[] secret) {
            try (var buffer = new Memory(secret.length)) {
                buffer.write(0, secret, 0, secret.length);
                try { return NativeApi.SECURITY.SecKeychainAddGenericPassword(null, service.length, service,
                        account.length, account, secret.length, buffer, null); }
                finally { buffer.clear(); }
            }
        }
    }
}
