package com.opencgl.lanmsg.security;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/** Exclusive local vault for identity, trust and bounded encrypted records. Never auto-resets a damaged vault. */
public final class LocalVault implements AutoCloseable {
    private static final byte[] CHECK = "OpenCGL LAN vault version 1".getBytes(StandardCharsets.US_ASCII);
    private final Path directory;
    private final LocalCipher cipher;
    private final FileChannel channel;
    private final FileLock lock;
    private byte[] reservedIdentity;
    private boolean closed;

    private LocalVault(Path directory, LocalCipher cipher, FileChannel channel, FileLock lock, byte[] reservedIdentity) {
        this.directory = directory; this.cipher = cipher; this.channel = channel; this.lock = lock;
        this.reservedIdentity = reservedIdentity;
    }

    public static LocalVault open(Path directory, SecretStore store) throws IOException {
        Objects.requireNonNull(store, "store");
        Path root = PrivateFiles.directory(directory);
        FileChannel channel = PrivateFiles.lockChannel(root.resolve("vault.lock"));
        FileLock lock = null; LocalCipher cipher = null; byte[] key = null;
        try {
            try { lock = channel.tryLock(); }
            catch (OverlappingFileLockException e) { throw new IOException("Vault is already open in another instance"); }
            if (lock == null) throw new IOException("Vault is already open in another instance");
            Path marker = root.resolve("vault.check");
            boolean initialized = Files.exists(marker, LinkOption.NOFOLLOW_LINKS);
            if (!initialized) {
                try (var entries = Files.list(root)) {
                    if (entries.anyMatch(p -> !p.getFileName().toString().equals("vault.lock")))
                        throw new IOException("Existing vault data has no valid marker; refusing to initialize");
                }
            }
            var stored = store.read();
            if (stored.isEmpty()) {
                if (initialized) throw new IOException("Vault key is missing; existing data has not been changed");
                key = new byte[32]; new SecureRandom().nextBytes(key);
                store.create(key);
                byte[] persisted = store.read().orElseThrow(() -> new IOException("System key store did not retain the key"));
                try {
                    if (!MessageDigest.isEqual(key, persisted)) throw new IOException("System key store verification failed");
                } finally { Arrays.fill(persisted, (byte) 0); }
            } else { key = stored.get(); }
            if (key.length != 32) throw new IOException("Invalid stored vault key; refusing to replace it");
            cipher = new LocalCipher(key);
            byte[] reservedIdentity = null;
            if (initialized) {
                byte[] decoded = cipher.open("vault-check", "v1", PrivateFiles.read(marker, 1024));
                try {
                    if (!MessageDigest.isEqual(CHECK, decoded)) {
                        if (decoded.length != CHECK.length + 32 || !MessageDigest.isEqual(CHECK, Arrays.copyOf(decoded, CHECK.length)))
                            throw new IOException("Vault marker is invalid");
                        reservedIdentity = Arrays.copyOfRange(decoded, CHECK.length, decoded.length);
                    }
                } finally { Arrays.fill(decoded, (byte) 0); }
            } else { PrivateFiles.atomicWrite(marker, cipher.seal("vault-check", "v1", CHECK)); }
            return new LocalVault(root, cipher, channel, lock, reservedIdentity);
        } catch (GeneralSecurityException e) {
            if (cipher != null) cipher.close();
            channel.close();
            throw new IOException("Vault authentication failed; check the original system key", e);
        } catch (IOException | RuntimeException | Error e) {
            if (cipher != null) cipher.close();
            try { channel.close(); } catch (IOException close) { e.addSuppressed(close); }
            throw e;
        } finally { if (key != null) Arrays.fill(key, (byte) 0); }
    }

    /** Blocking OS credential access; invoke on a background worker, never the JavaFX thread. */
    public static LocalVault openSystem(Path directory) throws IOException {
        Path root = PrivateFiles.directory(directory);
        return open(root, SystemSecretStore.forDirectory(root));
    }

    /** One-time identity reservation in the mandatory authenticated manifest, not an optional record. */
    synchronized boolean reserveDeviceIdentity(String deviceId) throws IOException {
        ensureOpen(); DeviceIdentity.canonicalId(deviceId);
        try {
            byte[] idHash = MessageDigest.getInstance("SHA-256").digest(deviceId.getBytes(StandardCharsets.US_ASCII));
            if (reservedIdentity != null) {
                if (!MessageDigest.isEqual(reservedIdentity, idHash)) throw new IOException("Vault belongs to a different device identity");
                return false;
            }
            byte[] manifest = Arrays.copyOf(CHECK, CHECK.length + idHash.length);
            System.arraycopy(idHash, 0, manifest, CHECK.length, idHash.length);
            // Latch in memory before persistence too: a rename succeeded followed by a directory-sync
            // failure must not let a caller retry initialization as though no reservation existed.
            reservedIdentity = idHash;
            PrivateFiles.atomicWrite(directory.resolve("vault.check"), cipher.seal("vault-check", "v1", manifest));
            return true;
        } catch (GeneralSecurityException e) { throw new IOException("Cannot reserve device identity", e); }
    }

    public synchronized Optional<byte[]> read(String purpose, String id) throws IOException {
        ensureOpen(); Path file = file(purpose, id);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        try { return Optional.of(cipher.open(purpose, id, PrivateFiles.read(file, LocalCipher.MAX_ENVELOPE_BYTES))); }
        catch (GeneralSecurityException e) { throw new IOException("Encrypted record authentication failed", e); }
    }

    public synchronized void write(String purpose, String id, byte[] value) throws IOException {
        ensureOpen(); Path file = file(purpose, id);
        try { PrivateFiles.atomicWrite(file, cipher.seal(purpose, id, value)); }
        catch (GeneralSecurityException e) { throw new IOException("Cannot encrypt local record", e); }
    }

    /** Authenticated envelopes for transactional stores; never exposes the vault key. */
    public synchronized byte[] sealRecord(String purpose, String id, byte[] value) throws IOException {
        ensureOpen();
        try { return cipher.seal(purpose, id, value); }
        catch (GeneralSecurityException e) { throw new IOException("Cannot encrypt local record", e); }
    }

    public synchronized byte[] openRecord(String purpose, String id, byte[] envelope) throws IOException {
        ensureOpen();
        try { return cipher.open(purpose, id, envelope); }
        catch (GeneralSecurityException e) { throw new IOException("Encrypted record authentication failed", e); }
    }

    private Path file(String purpose, String id) throws IOException {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(LocalCipher.context(purpose, id));
            return directory.resolve(HexFormat.of().formatHex(hash) + ".sealed");
        } catch (GeneralSecurityException e) { throw new IOException("SHA-256 is unavailable", e); }
    }

    private void ensureOpen() { if (closed) throw new IllegalStateException("Local vault is closed"); }

    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true; cipher.close();
        try { lock.release(); } finally { channel.close(); }
    }
}
