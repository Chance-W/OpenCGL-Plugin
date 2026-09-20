package com.opencgl.solace.service;

import com.opencgl.solace.model.SolaceConnectionConfig;
import com.opencgl.solace.model.SolaceOutboundMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolacePublisherServiceTest {

    @Test
    void usesAnIndependentReferenceStyleSessionForEverySend() throws Exception {
        List<FakeConnection> opened = new ArrayList<>();
        SolacePublisherService service = new SolacePublisherService(config -> {
            FakeConnection connection = new FakeConnection();
            opened.add(connection);
            return connection;
        });
        SolaceConnectionConfig config = new SolaceConnectionConfig();
        SolaceOutboundMessage first = message("orders/created");
        SolaceOutboundMessage second = message("orders/updated");

        SolacePublishReceipt firstReceipt = service.send(config, first).get(2, TimeUnit.SECONDS);
        service.send(config, second).get(2, TimeUnit.SECONDS);
        service.close();

        assertEquals(2, opened.size(), "each send must use the same isolated-session lifecycle as the working web implementation");
        assertEquals(List.of(first), opened.get(0).sent);
        assertEquals(List.of(second), opened.get(1).sent);
        assertEquals(SolacePublishReceipt.Status.BROKER_ACK, firstReceipt.status());
        assertTrue(opened.stream().allMatch(connection -> connection.closed),
            "every send session must close immediately after its result");
    }

    @Test
    void closesEverySessionEvenWhenTheSelectedConnectionChanges() throws Exception {
        List<FakeConnection> opened = new ArrayList<>();
        SolacePublisherService service = new SolacePublisherService(config -> {
            FakeConnection connection = new FakeConnection();
            opened.add(connection);
            return connection;
        });
        SolaceConnectionConfig first = new SolaceConnectionConfig(); first.setHost("tcp://one:55555");
        SolaceConnectionConfig second = new SolaceConnectionConfig(); second.setHost("tcp://two:55555");

        service.send(first, message("one")).get(2, TimeUnit.SECONDS);
        service.send(first, message("two")).get(2, TimeUnit.SECONDS);
        service.send(second, message("three")).get(2, TimeUnit.SECONDS);
        service.close();

        assertEquals(3, opened.size());
        assertTrue(opened.stream().allMatch(connection -> connection.closed));
    }

    @Test
    void closesAnInFlightSessionWhenThePublisherIsDisposed() throws Exception {
        FakeConnection connection = new FakeConnection();
        connection.pendingReceipt = new java.util.concurrent.CompletableFuture<>();
        SolacePublisherService service = new SolacePublisherService(config -> connection);

        var send = service.send(new SolaceConnectionConfig(), message("events/pending"));
        Thread.sleep(50);
        service.close();

        assertTrue(connection.closed);
        assertTrue(send.isDone());
    }

    private static SolaceOutboundMessage message(String destination) {
        return new SolaceOutboundMessage(SolaceOutboundMessage.DestinationType.TOPIC, destination,
            "{}", "application/json", null, null, null, null, 0, Map.of());
    }

    private static final class FakeConnection implements SolaceRuntimeConnection {
        private final List<SolaceOutboundMessage> sent = new ArrayList<>();
        private boolean closed;
        private java.util.concurrent.CompletableFuture<SolacePublishReceipt> pendingReceipt;

        @Override public java.util.concurrent.CompletableFuture<SolacePublishReceipt> send(SolaceOutboundMessage message) {
            sent.add(message);
            if (pendingReceipt != null) return pendingReceipt;
            return java.util.concurrent.CompletableFuture.completedFuture(new SolacePublishReceipt(
                SolacePublishReceipt.Status.BROKER_ACK, "test-key", System.currentTimeMillis(), "Broker ACK"));
        }
        @Override public ListenerHandle listen(SolaceListenerConfig config, SolaceRuntimeConnection.MessageHandler handler) {
            throw new UnsupportedOperationException();
        }
        @Override public void close() { closed = true; }
    }
}
