package com.opencgl.lanmsg.security;

import java.io.IOException;
import java.util.Optional;

/** Test-only substitute for native credential access. */
public final class MemorySecretStore implements SecretStore {
    private byte[] key;
    public synchronized Optional<byte[]> read() {return key==null?Optional.empty():Optional.of(key.clone());}
    public synchronized void create(byte[] value) throws IOException {
        if(key!=null)throw new IOException("Already initialized");key=value.clone();
    }
}
