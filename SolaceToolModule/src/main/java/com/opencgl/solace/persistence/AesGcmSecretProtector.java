package com.opencgl.solace.persistence;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Local-machine password protection used only when "remember password" is enabled. */
public final class AesGcmSecretProtector implements SecretProtector {
    private static final String PREFIX = "enc:v1:";
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmSecretProtector(String keyMaterial) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(keyMaterial.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    @Override public String protect(String value) {
        if (value == null || value.isEmpty()) return value;
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to protect Solace password", error);
        }
    }

    @Override public String unprotect(String value) {
        if (value == null || !value.startsWith(PREFIX)) return value;
        try {
            byte[] combined = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            byte[] iv = java.util.Arrays.copyOfRange(combined, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(combined, 12, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to restore Solace password", error);
        }
    }
}
