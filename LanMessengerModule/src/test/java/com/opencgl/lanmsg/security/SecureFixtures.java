package com.opencgl.lanmsg.security;

import com.opencgl.lanmsg.core.*;
import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/** Test-only OS-store substitution and explicit pre-trusted peers for existing protocol edge-case fixtures. */
public final class SecureFixtures {
    private static final Map<LanService, Remote> SERVICES = new IdentityHashMap<>();
    public static final class Remote implements AutoCloseable {
        public final LocalVault vault; public final DeviceIdentity identity;
        public final TrustRepository trust; public final SecureTransport transport;
        public Remote(Path directory, String id) throws Exception {
            vault = LocalVault.open(directory, new LocalVaultTest.MemoryStore());
            identity = DeviceIdentity.loadOrCreate(vault, id); trust = new TrustRepository(vault);
            transport = new SecureTransport(identity, trust, "测试端", Duration.ofSeconds(5), Duration.ZERO);
            transport.onPairing(p -> p.decision().complete(true));
        }
        public Socket accept(ServerSocket server) throws IOException { return transport.authorize(server.accept(),false,"",false); }
        public Socket connect(LanService peer) throws IOException { return transport.authorize(new Socket("127.0.0.1",peer.port()),true,peer.id(),false); }
        public void release(Socket socket) { transport.release(socket); }
        @Override public void close() throws Exception { transport.close(); vault.close(); }
    }
    public static LanService service(ChatRepository repo, Path directory, String name) throws Exception {
        return service(repo,directory,name,Duration.ofMinutes(10));
    }
    public static synchronized LanService service(ChatRepository repo, Path directory, String name, Duration deadline) throws Exception {
        var endpoint = new Remote(directory,repo.identity());
        var service = new LanService(repo,name,endpoint.transport,deadline); SERVICES.put(service,endpoint); return service;
    }
    public static synchronized Socket connect(LanService from, LanService to) throws IOException { return SERVICES.get(from).connect(to); }
    public static synchronized void trust(LanService service, Remote remote) throws IOException {
        var local = SERVICES.get(service);
        local.trust.approve(local.trust.inspect(remote.identity.deviceId(),remote.identity.fingerprint()));
        remote.trust.approve(remote.trust.inspect(local.identity.deviceId(),local.identity.fingerprint()));
    }
    public static synchronized void closeAll() throws Exception {
        Exception failure = null;
        for (var entry : SERVICES.entrySet()) {
            entry.getKey().close(); try { entry.getValue().close(); } catch (Exception e) { failure=e; }
        }
        SERVICES.clear(); if (failure!=null) throw failure;
    }
}
