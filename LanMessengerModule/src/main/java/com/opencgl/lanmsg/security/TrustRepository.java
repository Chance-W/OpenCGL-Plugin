package com.opencgl.lanmsg.security;

import java.io.*;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

/** Local authorization only. Transport still must obtain the other endpoint's approval for this connection. */
public final class TrustRepository {
    public enum Status { UNKNOWN, TRUSTED, CHANGED, REVOKED }
    public static final class Snapshot {
        private final TrustRepository owner;
        private final String deviceId, fingerprint;
        private final long generation;
        private final Status status;
        private Snapshot(TrustRepository owner, String id, String fingerprint, long generation, Status status) {
            this.owner = owner; this.deviceId = id; this.fingerprint = fingerprint;
            this.generation = generation; this.status = status;
        }
        public String deviceId() { return deviceId; }
        public String fingerprint() { return fingerprint; }
        public long generation() { return generation; }
        public Status status() { return status; }
    }
    private record Entry(long generation, boolean trusted, byte[] pin) { }
    private final LocalVault vault;
    public TrustRepository(LocalVault vault) { this.vault = java.util.Objects.requireNonNull(vault); }

    public Snapshot inspect(String id, String fingerprint) throws IOException {
        DeviceIdentity.canonicalId(id); byte[] pin = parsePin(fingerprint);
        synchronized (vault) {
            Entry entry = read(id);
            Status status = entry == null ? Status.UNKNOWN : !entry.trusted ? Status.REVOKED
                    : MessageDigest.isEqual(entry.pin, pin) ? Status.TRUSTED : Status.CHANGED;
            return new Snapshot(this, id, fingerprint, entry == null ? 0 : entry.generation, status);
        }
    }

    /** Call only after BOTH parties confirmed the same TLS pairing transcript. Never approve on discovery. */
    public void approve(Snapshot displayed) throws IOException {
        synchronized (vault) {
            if (displayed == null || displayed.owner != this) throw new IOException("Invalid pairing approval token");
            var current = inspect(displayed.deviceId, displayed.fingerprint);
            if (current.generation != displayed.generation || current.status != displayed.status)
                throw new IOException("Trust changed while pairing; approval is no longer valid");
            if (current.status != Status.UNKNOWN && current.status != Status.REVOKED)
                throw new IOException("Existing trust cannot be replaced without explicit revocation");
            write(current.deviceId, new Entry(next(current.generation), true, parsePin(current.fingerprint)));
        }
    }

    /** The connection owner must also close active sessions and cancel outstanding pairing prompts. */
    public void revoke(String id) throws IOException {
        DeviceIdentity.canonicalId(id);
        synchronized (vault) {
            Entry old = read(id);
            write(id, new Entry(next(old == null ? 0 : old.generation), false, old == null ? new byte[32] : old.pin));
        }
    }

    private Entry read(String id) throws IOException {
        byte[] bytes = vault.read("device-trust", id).orElse(null);
        if (bytes == null) return null;
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (bytes.length != 45 || input.readInt() != 0x4f435401) throw new IOException("Invalid trust record");
            long generation = input.readLong(); int trusted = input.readUnsignedByte(); byte[] pin = input.readNBytes(32);
            if (generation < 1 || trusted > 1) throw new IOException("Invalid trust record state");
            return new Entry(generation, trusted == 1, pin);
        } finally { Arrays.fill(bytes, (byte) 0); }
    }

    private void write(String id, Entry entry) throws IOException {
        var bytes = new ByteArrayOutputStream(45);
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(0x4f435401); out.writeLong(entry.generation); out.writeBoolean(entry.trusted); out.write(entry.pin);
        }
        byte[] data = bytes.toByteArray();
        try { vault.write("device-trust", id, data); }
        finally { Arrays.fill(data, (byte) 0); }
    }

    private static long next(long generation) throws IOException {
        if (generation == Long.MAX_VALUE) throw new IOException("Trust generation exhausted");
        return generation + 1;
    }
    private static byte[] parsePin(String pin) {
        if (pin == null || !pin.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Expected SHA-256 public key fingerprint");
        return HexFormat.of().parseHex(pin);
    }
}
