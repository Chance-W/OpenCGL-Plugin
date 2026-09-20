package com.opencgl.lanmsg.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PairingTranscriptTest {
    @TempDir Path root;
    @Test void verificationUsesAllBitsAndBindsBothKeysRolesAndFreshNonces() throws Exception {
        try (var av = LocalVault.open(root.resolve("a"), new LocalVaultTest.MemoryStore());
             var bv = LocalVault.open(root.resolve("b"), new LocalVaultTest.MemoryStore());
             var cv = LocalVault.open(root.resolve("c"), new LocalVaultTest.MemoryStore())) {
            var a = DeviceIdentity.loadOrCreate(av, UUID.randomUUID().toString()).certificate();
            var b = DeviceIdentity.loadOrCreate(bv, UUID.randomUUID().toString()).certificate();
            var c = DeviceIdentity.loadOrCreate(cv, UUID.randomUUID().toString()).certificate();
            byte[] an = new byte[32], bn = new byte[32]; bn[0] = 1;
            String code = PairingTranscript.verificationCode(a, an, b, bn);
            assertTrue(code.matches("[0-9A-F]{8}( [0-9A-F]{8}){7}"));
            assertEquals(code, PairingTranscript.verificationCode(a, an, b, bn));
            assertNotEquals(code, PairingTranscript.verificationCode(b, bn, a, an));
            assertNotEquals(code, PairingTranscript.verificationCode(c, an, b, bn));
            assertNotEquals(code, PairingTranscript.verificationCode(a, an, c, bn));
            an[1] = 1;
            assertNotEquals(code, PairingTranscript.verificationCode(a, an, b, bn));
            an[1] = 0; bn[2] = 2;
            assertNotEquals(code, PairingTranscript.verificationCode(a, an, b, bn));
            assertThrows(IllegalArgumentException.class, () -> PairingTranscript.verificationCode(a, new byte[31], b, bn));
            assertThrows(CertificateException.class, () -> PairingTranscript.verificationCode(a, an, a, bn));
        }
    }
}
