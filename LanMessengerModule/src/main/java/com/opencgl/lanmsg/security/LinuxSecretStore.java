package com.opencgl.lanmsg.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/** libsecret client: attributes are public; the secret is sent only over stdin. */
final class LinuxSecretStore implements SecretStore {
    @FunctionalInterface interface Runner {
        SecretToolRunner.Result run(List<String> arguments, byte[] input) throws IOException;
    }
    private final String profile;
    private final Runner runner;
    LinuxSecretStore(String profile) { this(profile, new SecretToolRunner()::run); }
    LinuxSecretStore(String profile, Runner runner) { this.profile = profile; this.runner = runner; }

    @Override public synchronized Optional<byte[]> read() throws IOException {
        try (var result = runner.run(List.of("lookup", "application", "OpenCGL.LanMessenger.v1", "profile", profile), new byte[0])) {
            // A cancelled unlock can also produce an empty lookup. Establish item absence separately.
            if (result.exitCode() == 1 && !result.hasError() && result.output().length == 0) {
                try (var inventory = runner.run(List.of("search", "--all", "application", "OpenCGL.LanMessenger.v1", "profile", profile), new byte[0])) {
                    if (inventory.exitCode() == 0 && !inventory.hasError() && inventory.output().length == 0)
                        return Optional.empty();
                    throw new IOException("Linux key exists but cannot be unlocked, or its existence cannot be verified");
                }
            }
            if (result.exitCode() != 0 || result.hasError()) throw new IOException("Linux Secret Service is unavailable, locked or denied access");
            byte[] key;
            try { key = Base64.getDecoder().decode(result.output()); }
            catch (IllegalArgumentException e) { throw new IOException("Invalid key in Linux Secret Service"); }
            if (key.length != 32) { Arrays.fill(key, (byte) 0); throw new IOException("Invalid key in Linux Secret Service"); }
            return Optional.of(key);
        }
    }

    @Override public synchronized void create(byte[] secret) throws IOException {
        if (secret == null || secret.length != 32) throw new IOException("A 256-bit key is required");
        byte[] existing = read().orElse(null);
        if (existing != null) { Arrays.fill(existing, (byte) 0); throw new IOException("Secret Service key already exists; not replacing it"); }
        byte[] encoded = Base64.getEncoder().encode(secret);
        try (var result = runner.run(List.of("store", "--label=OpenCGL LAN Messenger", "application", "OpenCGL.LanMessenger.v1", "profile", profile), encoded)) {
            if (result.exitCode() != 0 || result.hasError()) throw new IOException("Linux Secret Service could not save the key");
        } finally { Arrays.fill(encoded, (byte) 0); }
    }
}
