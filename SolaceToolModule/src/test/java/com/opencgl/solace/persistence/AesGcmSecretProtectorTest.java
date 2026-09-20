package com.opencgl.solace.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AesGcmSecretProtectorTest {
    @Test
    void encryptsRememberedPasswordAndCanRestoreIt() {
        AesGcmSecretProtector protector = new AesGcmSecretProtector("test-machine-key");

        String encrypted = protector.protect("broker-secret");

        assertTrue(encrypted.startsWith("enc:v1:"));
        assertNotEquals("broker-secret", encrypted);
        assertEquals("broker-secret", protector.unprotect(encrypted));
    }
}
