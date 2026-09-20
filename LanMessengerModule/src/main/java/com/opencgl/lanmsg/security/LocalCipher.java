package com.opencgl.lanmsg.security;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

/** Versioned, bounded AEAD records. This does not encrypt arbitrary large file streams. */
public final class LocalCipher implements AutoCloseable {
    private static final byte[] MAGIC = {'O', 'C', 'L', 'V', 1};
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BYTES = 16;
    public static final int MAX_PLAINTEXT_BYTES = 16 * 1024 * 1024;
    public static final int MAX_ENVELOPE_BYTES = MAX_PLAINTEXT_BYTES + MAGIC.length + NONCE_BYTES + TAG_BYTES;
    private final byte[] masterKey;
    private final SecureRandom random = new SecureRandom();
    private boolean closed;

    public LocalCipher(byte[] masterKey) {
        if (masterKey == null || masterKey.length != 32) throw new IllegalArgumentException("A 256-bit master key is required");
        this.masterKey = masterKey.clone();
    }

    public synchronized byte[] seal(String purpose, String record, byte[] plaintext) throws GeneralSecurityException {
        ensureOpen();
        Objects.requireNonNull(plaintext, "plaintext");
        if (plaintext.length > MAX_PLAINTEXT_BYTES) throw new IllegalArgumentException("Encrypted record is too large");
        byte[] context = context(purpose, record);
        byte[] nonce = new byte[NONCE_BYTES]; random.nextBytes(nonce);
        byte[] ciphertext = transform(Cipher.ENCRYPT_MODE, purpose, nonce, context, plaintext);
        return ByteBuffer.allocate(MAGIC.length + nonce.length + ciphertext.length)
                .put(MAGIC).put(nonce).put(ciphertext).array();
    }

    public synchronized byte[] open(String purpose, String record, byte[] envelope) throws GeneralSecurityException {
        ensureOpen();
        byte[] context = context(purpose, record);
        if (envelope == null || envelope.length < MAGIC.length + NONCE_BYTES + TAG_BYTES
                || envelope.length > MAX_ENVELOPE_BYTES
                || !Arrays.equals(MAGIC, Arrays.copyOf(envelope, MAGIC.length))) {
            throw new GeneralSecurityException("Invalid encrypted record format");
        }
        byte[] nonce = Arrays.copyOfRange(envelope, MAGIC.length, MAGIC.length + NONCE_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(envelope, MAGIC.length + NONCE_BYTES, envelope.length);
        return transform(Cipher.DECRYPT_MODE, purpose, nonce, context, ciphertext);
    }

    private byte[] transform(int mode, String purpose, byte[] nonce, byte[] context, byte[] input)
            throws GeneralSecurityException {
        // PRF-based derivation from a uniformly random master secret; distinct purpose keys.
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(masterKey, "HmacSHA256"));
        byte[] derived = mac.doFinal(context("OpenCGL-LAN/local-key/v1", purpose));
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(derived, "AES"), new GCMParameterSpec(TAG_BYTES * 8, nonce));
            cipher.updateAAD(MAGIC);
            cipher.updateAAD(context);
            return cipher.doFinal(input);
        } finally { Arrays.fill(derived, (byte) 0); }
    }

    static byte[] context(String purpose, String record) {
        if (purpose == null || purpose.isBlank() || record == null || record.isBlank())
            throw new IllegalArgumentException("Purpose and record identity are required");
        // Bound before UTF-8 allocation as well as after encoding.
        if (purpose.length() > 256 || record.length() > 4096) throw new IllegalArgumentException("Record context is too long");
        byte[] p = purpose.getBytes(StandardCharsets.UTF_8), r = record.getBytes(StandardCharsets.UTF_8);
        if (p.length > 256 || r.length > 4096) throw new IllegalArgumentException("Record context is too long");
        return ByteBuffer.allocate(8 + p.length + r.length).putInt(p.length).put(p).putInt(r.length).put(r).array();
    }

    private void ensureOpen() { if (closed) throw new IllegalStateException("Local cipher is closed"); }

    /** Best-effort clearing of owned bytes; the JVM/provider may retain internal copies. */
    @Override public synchronized void close() { Arrays.fill(masterKey, (byte) 0); closed = true; }
}
