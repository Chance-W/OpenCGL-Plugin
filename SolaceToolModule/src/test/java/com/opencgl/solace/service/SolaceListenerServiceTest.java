package com.opencgl.solace.service;

import com.opencgl.solace.model.SolaceConnectionConfig;
import com.opencgl.solace.model.SolaceReceivedMessage;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolaceListenerServiceTest {

    @Test
    void supportsPauseResumeAndBoundedMessageCollection() throws Exception {
        FakeConnection connection = new FakeConnection();
        SolaceListenerService service = new SolaceListenerService(config -> connection, 2);

        service.start(new SolaceConnectionConfig(),
            new SolaceListenerConfig(SolaceListenerConfig.DestinationType.TOPIC, "events/>", false))
            .get(2, TimeUnit.SECONDS);
        connection.emit(message("1"));
        connection.emit(message("2"));
        connection.emit(message("3"));
        service.pause();
        service.resume();

        assertEquals(1, service.droppedCount());
        var drained = service.drain(10);
        assertEquals("2", drained.get(0).body());
        assertEquals("3", drained.get(1).body());
        assertEquals(1, connection.handle.pauseCount);
        assertEquals(1, connection.handle.resumeCount);

        service.close();
        assertTrue(connection.handle.closed);
        assertTrue(connection.closed);
    }

    private static SolaceReceivedMessage message(String body) {
        return new SolaceReceivedMessage(1, "events/test", body, null, null, null,
            null, body.length(), body, Map.of());
    }

    private static final class FakeConnection implements SolaceRuntimeConnection {
        private SolaceRuntimeConnection.MessageHandler handler;
        private final FakeHandle handle = new FakeHandle();
        private boolean closed;
        @Override public java.util.concurrent.CompletableFuture<SolacePublishReceipt> send(
            com.opencgl.solace.model.SolaceOutboundMessage message) {
            return java.util.concurrent.CompletableFuture.completedFuture(new SolacePublishReceipt(
                SolacePublishReceipt.Status.DIRECT_ACCEPTED, "test", System.currentTimeMillis(), "accepted"));
        }
        @Override public ListenerHandle listen(SolaceListenerConfig config, SolaceRuntimeConnection.MessageHandler handler) {
            this.handler = handler;
            return handle;
        }
        void emit(SolaceReceivedMessage message) { handler.onMessage(message); }
        @Override public void close() { closed = true; }
    }

    private static final class FakeHandle implements SolaceRuntimeConnection.ListenerHandle {
        private int pauseCount;
        private int resumeCount;
        private boolean closed;
        @Override public void pause() { pauseCount++; }
        @Override public void resume() { resumeCount++; }
        @Override public void close() { closed = true; }
    }
}
