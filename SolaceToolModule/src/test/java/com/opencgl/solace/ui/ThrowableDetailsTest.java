package com.opencgl.solace.ui;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThrowableDetailsTest {

    @Test
    void unwrapsAsyncWrappersForSummary() {
        Throwable error = new CompletionException(
            new IllegalStateException("session failed", new SecurityException("Kerberos ticket missing")));

        assertEquals("Kerberos ticket missing", ThrowableDetails.summary(error));
    }

    @Test
    void rendersEveryMeaningfulCauseInsteadOfOnlyTheDeepestClassName() {
        Throwable error = new CompletionException(
            new IllegalStateException("session failed", new SecurityException("org.ietf.jgss.GSSException")));

        String details = ThrowableDetails.describe(error);

        assertTrue(details.contains("IllegalStateException: session failed"));
        assertTrue(details.contains("SecurityException: org.ietf.jgss.GSSException"));
        assertTrue(details.contains("建议检查 Solace 认证方案"));
    }
}
