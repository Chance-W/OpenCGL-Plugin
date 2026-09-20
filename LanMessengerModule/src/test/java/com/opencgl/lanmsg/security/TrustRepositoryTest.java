package com.opencgl.lanmsg.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TrustRepositoryTest {
    @TempDir Path root;
    private final String id = UUID.randomUUID().toString();
    private final String pin = "ab".repeat(32), changed = "cd".repeat(32);

    @Test void persistsAuthorizationAndNeverSilentlyAcceptsChangedKeys() throws Exception {
        var store = new LocalVaultTest.MemoryStore();
        try (var vault = LocalVault.open(root, store)) {
            var trust = new TrustRepository(vault);
            var initial = trust.inspect(id, pin);
            assertEquals(TrustRepository.Status.UNKNOWN, initial.status());
            trust.approve(initial);
            assertEquals(TrustRepository.Status.TRUSTED, trust.inspect(id, pin).status());
            var replacement = trust.inspect(id, changed);
            assertEquals(TrustRepository.Status.CHANGED, replacement.status());
            assertThrows(IOException.class, () -> trust.approve(replacement));
        }
        try (var vault = LocalVault.open(root, store)) {
            var trust = new TrustRepository(vault);
            assertEquals(TrustRepository.Status.TRUSTED, trust.inspect(id, pin).status());
            assertEquals(TrustRepository.Status.CHANGED, trust.inspect(id, changed).status());
        }
    }

    @Test void revocationPersistsAndInvalidatesAlreadyDisplayedApprovals() throws Exception {
        var store = new LocalVaultTest.MemoryStore();
        try (var vault = LocalVault.open(root, store)) {
            var trust = new TrustRepository(vault);
            var pending = trust.inspect(id, pin);
            trust.revoke(id);
            assertThrows(IOException.class, () -> trust.approve(pending));
        }
        try (var vault = LocalVault.open(root, store)) {
            var trust = new TrustRepository(vault);
            var pending = trust.inspect(id, changed);
            assertEquals(TrustRepository.Status.REVOKED, pending.status());
            trust.approve(pending);
            assertEquals(TrustRepository.Status.TRUSTED, trust.inspect(id, changed).status());
            assertThrows(IOException.class, () -> trust.approve(pending));
        }
    }

    @Test void rejectsCorruptionAndCrossRepositoryApprovalTokens() throws Exception {
        try (var vault = LocalVault.open(root, new LocalVaultTest.MemoryStore())) {
            var trust = new TrustRepository(vault);
            var another = new TrustRepository(vault);
            assertThrows(IOException.class, () -> another.approve(trust.inspect(id, pin)));
            vault.write("device-trust", id, new byte[]{1,2,3});
            assertThrows(IOException.class, () -> trust.inspect(id, pin));
            assertThrows(IllegalArgumentException.class, () -> trust.inspect(id, "wrong"));
            assertThrows(IllegalArgumentException.class, () -> trust.inspect("1-1-1-1-1", pin));
        }
    }

    @Test void concurrentRepositoryChangesInvalidateSnapshotsAndFailedWriteDoesNotAuthorize() throws Exception {
        var vault = LocalVault.open(root, new LocalVaultTest.MemoryStore());
        var trust = new TrustRepository(vault); var other = new TrustRepository(vault);
        var pending = trust.inspect(id, pin);
        other.revoke(id);
        assertThrows(IOException.class, () -> trust.approve(pending));
        var fresh = trust.inspect(id, pin);
        vault.close();
        assertThrows(IllegalStateException.class, () -> trust.approve(fresh));
    }
}
