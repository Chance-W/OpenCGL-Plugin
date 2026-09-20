package com.opencgl.lanmsg.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DeviceIdentityTest {
    @TempDir Path root;

    @Test void identitySurvivesReopenAndCreatesUsableKeyManagers() throws Exception {
        var store = new LocalVaultTest.MemoryStore();
        String id = UUID.randomUUID().toString();
        byte[] cert; String pin;
        try (var vault = LocalVault.open(root, store)) {
            var identity = DeviceIdentity.loadOrCreate(vault, id);
            cert = identity.certificate().getEncoded(); pin = identity.fingerprint();
            assertEquals(id, identity.deviceId());
            assertEquals(64, pin.length());
            assertTrue(identity.keyManagers().length > 0);
            assertEquals(id, DeviceIdentity.validateCertificate(identity.certificate()));
        }
        try (var vault = LocalVault.open(root, store)) {
            var restored = DeviceIdentity.loadOrCreate(vault, id);
            assertArrayEquals(cert, restored.certificate().getEncoded());
            assertEquals(pin, restored.fingerprint());
        }
        try (var files = Files.list(root)) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                assertFalse(new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8).contains(id));
            }
        }
    }

    @Test void differentDevicesUseDifferentKeysAndExistingIdCannotBeReassigned() throws Exception {
        try (var a = LocalVault.open(root.resolve("a"), new LocalVaultTest.MemoryStore());
             var b = LocalVault.open(root.resolve("b"), new LocalVaultTest.MemoryStore())) {
            String id = UUID.randomUUID().toString();
            var one = DeviceIdentity.loadOrCreate(a, id);
            var two = DeviceIdentity.loadOrCreate(b, id);
            assertNotEquals(one.fingerprint(), two.fingerprint());
            assertThrows(IOException.class, () -> DeviceIdentity.loadOrCreate(a, UUID.randomUUID().toString()));
            assertEquals(one.fingerprint(), DeviceIdentity.loadOrCreate(a, id).fingerprint());
        }
    }

    @Test void damagedCredentialIsNeverReplacedAndInvalidIdsCannotInitialize() throws Exception {
        try (var vault = LocalVault.open(root, new LocalVaultTest.MemoryStore())) {
            assertThrows(IllegalArgumentException.class, () -> DeviceIdentity.loadOrCreate(vault, "not-a-uuid"));
            assertTrue(vault.read("device-identity", "v1").isEmpty());
            String id = UUID.randomUUID().toString(); DeviceIdentity.loadOrCreate(vault, id);
            byte[] broken = {1,2,3}; vault.write("device-identity", "v1", broken);
            assertThrows(IOException.class, () -> DeviceIdentity.loadOrCreate(vault, id));
            assertArrayEquals(broken, vault.read("device-identity", "v1").orElseThrow());
        }
    }

    @Test void missingCredentialAfterInitializationDoesNotSilentlyGenerateNewKeys() throws Exception {
        try (var vault = LocalVault.open(root, new LocalVaultTest.MemoryStore())) {
            String id = UUID.randomUUID().toString();
            DeviceIdentity.loadOrCreate(vault, id);
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(LocalCipher.context("device-identity", "v1"));
            Files.delete(root.resolve(HexFormat.of().formatHex(hash) + ".sealed"));
            assertThrows(IOException.class, () -> DeviceIdentity.loadOrCreate(vault, id));
            assertTrue(vault.read("device-identity", "v1").isEmpty());
        }
    }

    @Test void losingBothIdentityRecordsDoesNotResetAnExistingVault() throws Exception {
        var store = new LocalVaultTest.MemoryStore(); String id = UUID.randomUUID().toString();
        try (var vault = LocalVault.open(root, store)) {
            DeviceIdentity.loadOrCreate(vault, id);
            new TrustRepository(vault).revoke(UUID.randomUUID().toString());
            for (String purpose : java.util.List.of("device-identity", "device-identity-state")) {
                byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(LocalCipher.context(purpose, "v1"));
                Files.deleteIfExists(root.resolve(HexFormat.of().formatHex(hash) + ".sealed"));
            }
        }
        try (var vault = LocalVault.open(root, store)) {
            assertThrows(IOException.class, () -> DeviceIdentity.loadOrCreate(vault, id));
            assertTrue(vault.read("device-identity", "v1").isEmpty());
        }
    }

    @Test void expiredWrongUsageOrMismatchedPrivateKeyAreRejected() throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("EC");
        generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        var pair = generator.generateKeyPair(); String id = UUID.randomUUID().toString();
        var name = new org.bouncycastle.asn1.x500.X500Name("CN=" + id);
        var now = java.time.Instant.now();
        var expired = new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(name, java.math.BigInteger.ONE,
                Date.from(now.minusSeconds(100)), Date.from(now.minusSeconds(10)), name, pair.getPublic());
        var signer = new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withECDSA").build(pair.getPrivate());
        var converter = new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter();
        assertThrows(java.security.cert.CertificateExpiredException.class,
                () -> DeviceIdentity.validateCertificate(converter.getCertificate(expired.build(signer))));
        var noUsage = new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(name, java.math.BigInteger.TWO,
                Date.from(now.minusSeconds(10)), Date.from(now.plusSeconds(100)), name, pair.getPublic());
        assertThrows(java.security.cert.CertificateException.class,
                () -> DeviceIdentity.validateCertificate(converter.getCertificate(noUsage.build(signer))));
        try (var vault = LocalVault.open(root, new LocalVaultTest.MemoryStore())) {
            var valid = DeviceIdentity.loadOrCreate(vault, id);
            var keys = java.security.KeyStore.getInstance("PKCS12"); keys.load(null, new char[0]);
            keys.setKeyEntry("device", pair.getPrivate(), new char[0], new java.security.cert.Certificate[]{valid.certificate()});
            var output = new java.io.ByteArrayOutputStream(); keys.store(output, new char[0]);
            vault.write("device-identity", "v1", output.toByteArray());
            assertThrows(IOException.class, () -> DeviceIdentity.loadOrCreate(vault, id));
        }
    }
}
