package com.opencgl.lanmsg.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SecureTransportTest {
    @TempDir Path root;
    static final class Endpoint implements AutoCloseable {
        final LocalVault vault; final DeviceIdentity identity; final TrustRepository trust; final SecureTransport transport;
        final BlockingQueue<SecureTransport.PairingRequest> prompts = new LinkedBlockingQueue<>();
        Endpoint(Path root, Duration timeout) throws Exception {
            vault = LocalVault.open(root, new LocalVaultTest.MemoryStore());
            identity = DeviceIdentity.loadOrCreate(vault, UUID.randomUUID().toString()); trust = new TrustRepository(vault);
            transport = new SecureTransport(identity, trust, "中文设备📎", timeout, Duration.ZERO);
            transport.onPairing(prompts::add);
        }
        @Override public void close() throws Exception { transport.close(); vault.close(); }
    }
    static final class Link implements AutoCloseable {
        final ServerSocket server = new ServerSocket(0, 4, InetAddress.getLoopbackAddress());
        final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        final Future<SSLSocket> inbound, outbound;
        Link(Endpoint a, Endpoint b, boolean pairing) throws IOException {
            inbound = pool.submit(() -> b.transport.authorize(server.accept(), false, "", false));
            outbound = pool.submit(() -> a.transport.authorize(new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort()), true, b.identity.deviceId(), pairing));
        }
        @Override public void close() throws Exception { server.close(); inbound.cancel(true); outbound.cancel(true); pool.shutdownNow(); }
    }
    @Test void bothPartiesMustConfirmBeforeChineseBusinessDataAndReconnectNeedsNoPrompt() throws Exception {
        try (var a = new Endpoint(root.resolve("a"), Duration.ofSeconds(5));
             var b = new Endpoint(root.resolve("b"), Duration.ofSeconds(5))) {
            try (var link = new Link(a, b, true)) {
                var ap = a.prompts.poll(3, TimeUnit.SECONDS); var bp = b.prompts.poll(3, TimeUnit.SECONDS);
                assertNotNull(ap); assertNotNull(bp); assertEquals(ap.verificationCode(), bp.verificationCode());
                assertEquals(b.identity.fingerprint(), ap.fingerprint());
                ap.decision().complete(true);
                assertThrows(TimeoutException.class, () -> link.outbound.get(150, TimeUnit.MILLISECONDS));
                assertFalse(link.inbound.isDone());
                bp.decision().complete(true);
                try (var out = link.outbound.get(3, TimeUnit.SECONDS); var in = link.inbound.get(3, TimeUnit.SECONDS)) {
                    assertEquals("TLSv1.3", out.getSession().getProtocol());
                    new DataOutputStream(out.getOutputStream()).writeUTF("中文消息📎\n第二行");
                    assertEquals("中文消息📎\n第二行", new DataInputStream(in.getInputStream()).readUTF());
                    a.transport.release(out); b.transport.release(in);
                }
            }
            try (var link = new Link(a, b, false); var out = link.outbound.get(3, TimeUnit.SECONDS); var in = link.inbound.get(3, TimeUnit.SECONDS)) {
                assertTrue(a.prompts.isEmpty()); assertTrue(b.prompts.isEmpty());
                assertEquals(b.identity.deviceId(), a.transport.peerId(out));
                a.transport.release(out); b.transport.release(in);
            }
        }
    }
    @Test void rejectionCancelsOtherPromptAndDoesNotCreateTrust() throws Exception {
        try (var a = new Endpoint(root.resolve("a"), Duration.ofSeconds(5)); var b = new Endpoint(root.resolve("b"), Duration.ofSeconds(5)); var link = new Link(a,b,true)) {
            var ap = a.prompts.poll(3, TimeUnit.SECONDS); var bp = b.prompts.poll(3, TimeUnit.SECONDS);
            assertNotNull(ap); assertNotNull(bp); bp.decision().complete(false);
            assertThrows(ExecutionException.class, () -> link.inbound.get(2,TimeUnit.SECONDS));
            assertThrows(ExecutionException.class, () -> link.outbound.get(2,TimeUnit.SECONDS));
            assertTrue(ap.decision().isDone());
            assertEquals(TrustRepository.Status.UNKNOWN,a.trust.inspect(b.identity.deviceId(),b.identity.fingerprint()).status());
        }
    }
    @Test void timeoutClosesUndecidedConnectionWithoutTrust() throws Exception {
        try (var a = new Endpoint(root.resolve("a"), Duration.ofMillis(800)); var b = new Endpoint(root.resolve("b"), Duration.ofMillis(800)); var link = new Link(a,b,true)) {
            assertThrows(ExecutionException.class, () -> link.outbound.get(3,TimeUnit.SECONDS));
            assertThrows(ExecutionException.class, () -> link.inbound.get(3,TimeUnit.SECONDS));
            assertEquals(TrustRepository.Status.UNKNOWN,a.trust.inspect(b.identity.deviceId(),b.identity.fingerprint()).status());
        }
    }
    @Test void ordinaryUnpairedConnectionDoesNotPromptOrAuthorize() throws Exception {
        try (var a = new Endpoint(root.resolve("a"), Duration.ofSeconds(3)); var b = new Endpoint(root.resolve("b"), Duration.ofSeconds(3)); var link = new Link(a,b,false)) {
            assertThrows(ExecutionException.class, () -> link.outbound.get(3,TimeUnit.SECONDS));
            assertThrows(ExecutionException.class, () -> link.inbound.get(3,TimeUnit.SECONDS));
            assertTrue(a.prompts.isEmpty()); assertTrue(b.prompts.isEmpty());
        }
    }
    @Test void changedPinnedKeyFailsEvenWhenExplicitPairingWasRequested() throws Exception {
        try (var a=new Endpoint(root.resolve("a"),Duration.ofSeconds(3));var b=new Endpoint(root.resolve("b"),Duration.ofSeconds(3))) {
            a.trust.approve(a.trust.inspect(b.identity.deviceId(),"00".repeat(32)));
            try(var link=new Link(a,b,true)) {
                assertThrows(ExecutionException.class,()->link.outbound.get(3,TimeUnit.SECONDS));
                assertThrows(ExecutionException.class,()->link.inbound.get(3,TimeUnit.SECONDS));
                assertTrue(a.prompts.isEmpty());assertTrue(b.prompts.isEmpty());
            }
        }
    }
    @Test void revocationCancelsPendingApprovalAndOldConfirmationCannotAuthorize() throws Exception {
        try(var a=new Endpoint(root.resolve("a"),Duration.ofSeconds(3));var b=new Endpoint(root.resolve("b"),Duration.ofSeconds(3));var link=new Link(a,b,true)) {
            var ap=a.prompts.poll(2,TimeUnit.SECONDS);var bp=b.prompts.poll(2,TimeUnit.SECONDS);
            assertNotNull(ap);assertNotNull(bp);a.transport.revoke(b.identity.deviceId());
            ap.decision().complete(true);bp.decision().complete(true);
            assertThrows(ExecutionException.class,()->link.outbound.get(2,TimeUnit.SECONDS));
            assertThrows(ExecutionException.class,()->link.inbound.get(2,TimeUnit.SECONDS));
            assertEquals(TrustRepository.Status.REVOKED,a.trust.inspect(b.identity.deviceId(),b.identity.fingerprint()).status());
        }
    }
    @Test void revocationClosesActiveSocketAndBlocksOrdinaryReconnect() throws Exception {
        try(var a=new Endpoint(root.resolve("a"),Duration.ofSeconds(3));var b=new Endpoint(root.resolve("b"),Duration.ofSeconds(3))) {
            a.trust.approve(a.trust.inspect(b.identity.deviceId(),b.identity.fingerprint()));
            b.trust.approve(b.trust.inspect(a.identity.deviceId(),a.identity.fingerprint()));
            try(var link=new Link(a,b,false)) {
                var out=link.outbound.get(3,TimeUnit.SECONDS);var in=link.inbound.get(3,TimeUnit.SECONDS);
                a.transport.revoke(b.identity.deviceId());
                assertThrows(IOException.class,()->a.transport.peerId(out));
                in.setSoTimeout(1000);
                try{assertEquals(-1,in.getInputStream().read());}catch(javax.net.ssl.SSLException expected){}
                a.transport.release(out);b.transport.release(in);
            }
            try(var link=new Link(a,b,false)) {
                assertThrows(ExecutionException.class,()->link.outbound.get(2,TimeUnit.SECONDS));
                assertThrows(ExecutionException.class,()->link.inbound.get(2,TimeUnit.SECONDS));
                assertTrue(a.prompts.isEmpty());assertTrue(b.prompts.isEmpty());
            }
        }
    }
    @Test void remoteApprovalFollowedByDisconnectCancelsTheStillUndecidedLocalPrompt() throws Exception {
        try(var a=new Endpoint(root.resolve("a"),Duration.ofSeconds(5));var b=new Endpoint(root.resolve("b"),Duration.ofSeconds(5));var link=new Link(a,b,true)) {
            var ap=a.prompts.poll(2,TimeUnit.SECONDS);var bp=b.prompts.poll(2,TimeUnit.SECONDS);
            assertNotNull(ap);assertNotNull(bp);bp.decision().complete(true);
            assertThrows(TimeoutException.class,()->link.inbound.get(200,TimeUnit.MILLISECONDS));
            b.transport.close();
            assertFalse(ap.decision().get(1,TimeUnit.SECONDS));
            assertThrows(ExecutionException.class,()->link.outbound.get(1,TimeUnit.SECONDS));
        }
    }
    @Test void expectedDeviceMismatchFailsBeforePrompting() throws Exception {
        try(var a=new Endpoint(root.resolve("a"),Duration.ofSeconds(3));var b=new Endpoint(root.resolve("b"),Duration.ofSeconds(3));
            var server=new ServerSocket(0,4,InetAddress.getLoopbackAddress());var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var inbound=pool.submit(()->b.transport.authorize(server.accept(),false,"",false));
            assertThrows(IOException.class,()->a.transport.authorize(new Socket(InetAddress.getLoopbackAddress(),server.getLocalPort()),true,UUID.randomUUID().toString(),true));
            assertThrows(ExecutionException.class,()->inbound.get(2,TimeUnit.SECONDS));
            assertTrue(a.prompts.isEmpty());assertTrue(b.prompts.isEmpty());
        }
    }
}
