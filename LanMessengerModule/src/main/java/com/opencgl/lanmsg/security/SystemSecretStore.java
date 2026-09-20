package com.opencgl.lanmsg.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class SystemSecretStore {
    private SystemSecretStore() { }

    /** The private directory must already exist. No secrets are read or written by this factory. */
    public static SecretStore forDirectory(Path directory) throws IOException {
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("System secret store requires an existing private directory");
        Path root = directory.toRealPath();
        String profile;
        try { profile = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(root.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IOException("SHA-256 is unavailable", e); }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.startsWith("mac")) return new MacKeychainStore(profile);
        if (os.startsWith("windows")) return new WindowsDpapiStore(root.resolve("master.dpapi"));
        if (os.startsWith("linux")) return new LinuxSecretStore(profile);
        throw new IOException("No supported system key store on this operating system");
    }
}
