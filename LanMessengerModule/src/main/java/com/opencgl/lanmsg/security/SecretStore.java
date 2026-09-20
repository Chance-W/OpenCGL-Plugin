package com.opencgl.lanmsg.security;

import java.io.IOException;
import java.util.Optional;

/** OS credential boundary. Empty means genuinely absent, not unavailable or denied.
 * Callers must serialize read/create with the vault's exclusive process lock. */
public interface SecretStore {
    Optional<byte[]> read() throws IOException;
    void create(byte[] secret) throws IOException;
}
