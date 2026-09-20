package com.opencgl.lanmsg.security;

import javax.net.ssl.*;
import java.io.*;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** TLS plus application authorization. No stream is returned until both endpoints authorize this connection. */
public final class SecureTransport implements AutoCloseable {
    public record PairingRequest(String deviceId, String name, String address, String fingerprint,
                                 String verificationCode, CompletableFuture<Boolean> decision) { }
    private static final int MAGIC = 0x4f435303;
    private final DeviceIdentity identity;
    private final TrustRepository trust;
    private final String name;
    private final SSLContext tls;
    private final long timeoutMillis, cooldownNanos;
    private final Semaphore handshakes = new Semaphore(8), promptSlot = new Semaphore(1);
    private final Map<Socket, Connection> connections = new HashMap<>();
    private final ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1, task -> {
        var thread = new Thread(task, "lan-pairing-deadline"); thread.setDaemon(true); return thread;
    });
    private volatile Consumer<PairingRequest> pairing = request -> request.decision().complete(false);
    private boolean closed;
    private long lastPrompt;

    private static final class Connection {
        final Socket raw;
        SSLSocket socket;
        volatile String peerId;
        volatile CompletableFuture<Boolean> decision;
        boolean authorized;
        Connection(Socket raw) { this.raw = raw; }
        void abort() {
            // Close the underlying socket first, including when JSSE is blocked writing.
            try { raw.close(); } catch (IOException ignored) { }
            var pending = decision; if (pending != null) pending.complete(false);
        }
    }
    private record Hello(String name, byte[] nonce, boolean pair, boolean trusted) { }

    public SecureTransport(DeviceIdentity identity, TrustRepository trust, String name) throws IOException {
        this(identity, trust, name, Duration.ofSeconds(120), Duration.ofSeconds(5));
    }
    SecureTransport(DeviceIdentity identity, TrustRepository trust, String name, Duration timeout, Duration cooldown) throws IOException {
        this.identity = Objects.requireNonNull(identity); this.trust = Objects.requireNonNull(trust);
        if (name == null || name.isBlank() || name.length() > 256) throw new IllegalArgumentException("Invalid device name");
        if (timeout.isNegative() || timeout.isZero() || cooldown.isNegative()) throw new IllegalArgumentException("Invalid pairing deadline");
        this.name = name; timeoutMillis = Math.max(1, timeout.toMillis()); cooldownNanos = cooldown.toNanos();
        timer.setRemoveOnCancelPolicy(true);
        try {
            tls = SSLContext.getInstance("TLSv1.3");
            tls.init(identity.keyManagers(), new TrustManager[]{new DeviceCertificates()}, new java.security.SecureRandom());
        } catch (GeneralSecurityException e) { timer.shutdownNow(); throw new IOException("Cannot initialize secure transport", e); }
    }
    public String deviceId() { return identity.deviceId(); }
    public void onPairing(Consumer<PairingRequest> listener) { pairing = Objects.requireNonNull(listener); }

    public SSLSocket authorize(Socket raw, boolean initiator, String expectedId, boolean requestPairing) throws IOException {
        Objects.requireNonNull(raw);
        if (!handshakes.tryAcquire()) { raw.close(); throw new IOException("Too many pending secure connections"); }
        var connection = new Connection(raw); ScheduledFuture<?> deadline = null; boolean acquiredPrompt = false, success = false;
        Thread reader = null;
        try {
            synchronized (this) {
                if (closed) throw new IOException("Secure transport closed");
                connections.put(raw, connection);
                deadline = timer.schedule(connection::abort, timeoutMillis, TimeUnit.MILLISECONDS);
            }
            var socket = (SSLSocket) tls.getSocketFactory().createSocket(raw, raw.getInetAddress().getHostAddress(), raw.getPort(), true);
            connection.socket = socket;
            socket.setUseClientMode(initiator); socket.setEnabledProtocols(new String[]{"TLSv1.3"});
            socket.setEnabledCipherSuites(new String[]{"TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384"});
            if (!initiator) socket.setNeedClientAuth(true);
            socket.setSoTimeout((int)Math.min(Integer.MAX_VALUE, timeoutMillis)); socket.startHandshake();
            var cert = (X509Certificate)socket.getSession().getPeerCertificates()[0];
            String peerId = DeviceIdentity.validateCertificate(cert);
            if (peerId.equals(identity.deviceId())) throw new IOException("Cannot connect to this device itself");
            if (expectedId != null && !expectedId.isEmpty() && !expectedId.equals(peerId)) throw new IOException("Peer device identity changed");
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getPublicKey().getEncoded()));
            TrustRepository.Snapshot snapshot;
            synchronized (this) {
                ensureOpen(connection); connection.peerId = peerId; snapshot = trust.inspect(peerId, fingerprint);
            }
            if (snapshot.status() == TrustRepository.Status.CHANGED) throw new IOException("Peer key changed; revoke old trust and verify again");
            byte[] nonce = new byte[32]; new java.security.SecureRandom().nextBytes(nonce);
            var input = new DataInputStream(socket.getInputStream()); var output = new DataOutputStream(socket.getOutputStream());
            writeHello(output, new Hello(name, nonce, initiator && requestPairing, snapshot.status() == TrustRepository.Status.TRUSTED));
            Hello hello = readHello(input);
            boolean needsPairing = snapshot.status() != TrustRepository.Status.TRUSTED || !hello.trusted;
            var remoteReady = new CompletableFuture<Boolean>();
            if (needsPairing) {
                if (!(initiator ? requestPairing : hello.pair)) throw new IOException("Device not paired; use explicit Connect to verify");
                String code = initiator ? PairingTranscript.verificationCode(identity.certificate(), nonce, cert, hello.nonce)
                        : PairingTranscript.verificationCode(cert, hello.nonce, identity.certificate(), nonce);
                CompletableFuture<Boolean> decision = new CompletableFuture<>();
                synchronized (this) {
                    ensureOpen(connection);
                    long now = System.nanoTime();
                    if ((lastPrompt != 0 && now - lastPrompt < cooldownNanos) || !promptSlot.tryAcquire())
                        throw new IOException("Pairing busy; retry shortly");
                    acquiredPrompt = true; lastPrompt = now; connection.decision = decision;
                    if (raw.isClosed()) decision.complete(false);
                }
                var remote = new CompletableFuture<Boolean>();
                reader = Thread.ofVirtual().name("lan-pairing-confirmation").start(() -> {
                    try {
                        boolean accepted = input.readBoolean();
                        if (accepted && !code.equals(readVerification(input))) throw new IOException("Pairing transcript changed");
                        remote.complete(accepted);
                        if (!accepted) { connection.abort(); remoteReady.complete(false); }
                        else {
                            // Keep observing EOF after acceptance while the local dialog is still open.
                            if (input.readInt() != MAGIC || !Boolean.TRUE.equals(decision.getNow(false)))
                                throw new IOException("Peer became ready before bilateral confirmation");
                            remoteReady.complete(true);
                        }
                    } catch (Exception e) { remote.completeExceptionally(e); remoteReady.completeExceptionally(e); connection.abort(); }
                });
                try { pairing.accept(new PairingRequest(peerId, hello.name, raw.getRemoteSocketAddress().toString(), fingerprint, code, decision)); }
                catch (RuntimeException e) { decision.complete(false); }
                boolean accepted = Boolean.TRUE.equals(decision.get(timeoutMillis, TimeUnit.MILLISECONDS));
                output.writeBoolean(accepted); if (accepted) output.writeUTF(code); output.flush();
                if (!accepted || !Boolean.TRUE.equals(remote.get(timeoutMillis, TimeUnit.MILLISECONDS))) throw new IOException("Pairing rejected or expired");
                synchronized (this) {
                    ensureOpen(connection); assertUnchanged(snapshot);
                    if (snapshot.status() != TrustRepository.Status.TRUSTED) trust.approve(snapshot);
                    snapshot = trust.inspect(peerId, fingerprint);
                }
            }
            // Both sides must finish local durable authorization before any business bytes are exposed.
            output.writeInt(MAGIC); output.flush();
            if (needsPairing ? !Boolean.TRUE.equals(remoteReady.get(timeoutMillis,TimeUnit.MILLISECONDS)) : input.readInt() != MAGIC)
                throw new IOException("Secure peer is not ready");
            synchronized (this) {
                ensureOpen(connection); assertUnchanged(snapshot);
                if (snapshot.status() != TrustRepository.Status.TRUSTED) throw new IOException("Peer is not trusted");
                deadline.cancel(false); socket.setSoTimeout(15000);
                connections.remove(raw); connections.put(socket, connection); connection.authorized = true; success = true;
            }
            return socket;
        } catch (IOException e) { throw e; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Secure connection interrupted", e); }
        catch (Exception e) { throw new IOException("Secure connection or pairing failed", e); }
        finally {
            if (deadline != null) deadline.cancel(false);
            if (!success) { connection.abort(); synchronized (this) { connections.remove(raw); } }
            if (reader != null && reader.isAlive()) reader.interrupt();
            if (acquiredPrompt) promptSlot.release();
            handshakes.release();
        }
    }

    private void assertUnchanged(TrustRepository.Snapshot previous) throws IOException {
        var current = trust.inspect(previous.deviceId(), previous.fingerprint());
        if (current.generation() != previous.generation() || current.status() != previous.status())
            throw new IOException("Trust changed during connection authorization");
    }
    private void ensureOpen(Connection connection) throws IOException {
        if (closed || connection.raw.isClosed()) throw new IOException("Secure connection closed or expired");
    }
    public synchronized String peerId(Socket socket) throws IOException {
        var connection = connections.get(socket);
        if (connection == null || !connection.authorized) throw new IOException("Connection is not authorized");
        ensureOpen(connection); return connection.peerId;
    }
    public void release(Socket socket) {
        Connection connection;
        synchronized (this) { connection = connections.remove(socket); }
        if (connection != null) connection.abort();
        try { socket.close(); } catch (IOException ignored) { }
    }
    public void revoke(String peerId) throws IOException {
        List<Connection> revoked;
        synchronized (this) {
            trust.revoke(peerId);
            revoked = connections.values().stream().filter(c -> peerId.equals(c.peerId)).toList();
            connections.values().removeIf(c -> peerId.equals(c.peerId));
        }
        revoked.forEach(Connection::abort);
    }
    @Override public void close() {
        List<Connection> active;
        synchronized (this) { if (closed) return; closed = true; active = List.copyOf(connections.values()); connections.clear(); }
        active.forEach(Connection::abort); timer.shutdownNow();
    }
    private static void writeHello(DataOutputStream output, Hello hello) throws IOException {
        output.writeInt(MAGIC); output.writeUTF(hello.name); output.write(hello.nonce);
        output.writeBoolean(hello.pair); output.writeBoolean(hello.trusted); output.flush();
    }
    private static String readVerification(DataInputStream input) throws IOException {
        int length=input.readUnsignedShort();
        if(length!=71)throw new IOException("Invalid pairing verification length");
        byte[] code=new byte[length];input.readFully(code);
        return new String(code,java.nio.charset.StandardCharsets.US_ASCII);
    }
    private static Hello readHello(DataInputStream input) throws IOException {
        if (input.readInt() != MAGIC) throw new IOException("Unsupported secure protocol");
        // DataInputStream.readUTF allocates according to the prefix; bound it BEFORE allocation.
        int length = input.readUnsignedShort(); if (length > 768) throw new IOException("Device name is too long");
        byte[] utf = new byte[length + 2]; utf[0] = (byte)(length >>> 8); utf[1] = (byte)length;
        input.readFully(utf, 2, length);
        String name = new DataInputStream(new ByteArrayInputStream(utf)).readUTF();
        if (name.isBlank() || name.length() > 256) throw new IOException("Invalid device name");
        byte[] nonce = new byte[32]; input.readFully(nonce);
        return new Hello(name, nonce, input.readBoolean(), input.readBoolean());
    }

    /** Structural certificate validation permits ONLY the bounded pairing protocol, not business traffic. */
    private static final class DeviceCertificates extends X509ExtendedTrustManager {
        private void check(X509Certificate[] chain) throws CertificateException {
            if (chain == null || chain.length != 1) throw new CertificateException("One device certificate required");
            try { DeviceIdentity.validateCertificate(chain[0]); }
            catch (GeneralSecurityException | RuntimeException e) { throw new CertificateException("Invalid device certificate", e); }
        }
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        public void checkClientTrusted(X509Certificate[] c,String a) throws CertificateException { check(c); }
        public void checkServerTrusted(X509Certificate[] c,String a) throws CertificateException { check(c); }
        public void checkClientTrusted(X509Certificate[] c,String a,Socket s) throws CertificateException { check(c); }
        public void checkServerTrusted(X509Certificate[] c,String a,Socket s) throws CertificateException { check(c); }
        public void checkClientTrusted(X509Certificate[] c,String a,SSLEngine e) throws CertificateException { check(c); }
        public void checkServerTrusted(X509Certificate[] c,String a,SSLEngine e) throws CertificateException { check(c); }
    }
}
