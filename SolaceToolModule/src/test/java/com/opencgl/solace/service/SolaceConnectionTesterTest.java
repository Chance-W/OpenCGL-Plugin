package com.opencgl.solace.service;

import com.opencgl.solace.model.SolaceConnectionConfig;
import com.opencgl.solace.model.SolaceOutboundMessage;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolaceConnectionTesterTest {

    @Test
    void closesSuccessfulProbeConnection() throws Exception {
        FakeConnection connection = new FakeConnection();
        SolaceConnectionTester tester = new SolaceConnectionTester(config -> connection);

        tester.test(new SolaceConnectionConfig()).get(2, TimeUnit.SECONDS);

        assertTrue(connection.closed);
    }

    @Test
    void exposesConnectionFailureToTheUiCallback() {
        SolaceConnectionTester tester = new SolaceConnectionTester(config -> {
            throw new IllegalStateException("broker unavailable");
        });

        assertThrows(Exception.class,
            () -> tester.test(new SolaceConnectionConfig()).get(2, TimeUnit.SECONDS));
    }

    private static final class FakeConnection implements SolaceRuntimeConnection {
        private boolean closed;
        @Override public CompletableFuture<SolacePublishReceipt> send(SolaceOutboundMessage message) {
            throw new UnsupportedOperationException();
        }
        @Override public ListenerHandle listen(SolaceListenerConfig config, MessageHandler handler) {
            throw new UnsupportedOperationException();
        }
        @Override public void close() { closed = true; }
    }
}
