package com.opencgl.solace.service;

import com.opencgl.solace.model.SolaceOutboundMessage;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.TextMessage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SolaceMessageMapperTest {

    private final SolaceMessageMapper mapper = new SolaceMessageMapper();

    @Test
    void mapsTextOutboundMessageAndTopicDestination() throws Exception {
        var outbound = new SolaceOutboundMessage(
            SolaceOutboundMessage.DestinationType.TOPIC,
            "orders/new",
            "{\"id\":1}",
            "application/json",
            "gzip",
            "corr-1",
            "reply/orders",
            DeliveryMode.PERSISTENT,
            30_000,
            Map.of("tenant", "cn", "traceId", "t-1")
        );

        var mapped = mapper.toJcsmp(outbound);

        assertEquals("orders/new", mapped.destination().getName());
        assertEquals("{\"id\":1}", ((TextMessage) mapped.message()).getText());
        assertEquals("application/json", mapped.message().getHTTPContentType());
        assertEquals("gzip", mapped.message().getHTTPContentEncoding());
        assertEquals("corr-1", mapped.message().getCorrelationId());
        assertEquals("reply/orders", mapped.message().getReplyTo().getName());
        assertEquals(DeliveryMode.PERSISTENT, mapped.message().getDeliveryMode());
        assertTrue(mapped.message().isAckImmediately(), "persistent sends must request an immediate broker ACK");
        assertEquals(30_000, mapped.message().getTimeToLive());
        assertEquals("cn", mapped.message().getProperties().getString("tenant"));
    }

    @Test
    void directMessagesDoNotRequestBrokerAcknowledgement() throws Exception {
        var outbound = new SolaceOutboundMessage(
            SolaceOutboundMessage.DestinationType.TOPIC, "events/direct", "body",
            null, null, null, null, DeliveryMode.DIRECT, 0, Map.of());

        var mapped = mapper.toJcsmp(outbound);

        assertFalse(mapped.message().isAckImmediately());
    }

    @Test
    void mapsInboundTextMessageWithoutMutatingIt() throws Exception {
        TextMessage message = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
        message.setText("hello");
        message.setHTTPContentType("text/plain");
        message.setCorrelationId("corr-2");
        message.setDeliveryMode(DeliveryMode.DIRECT);
        var properties = JCSMPFactory.onlyInstance().createMap();
        properties.putString("source", "test");
        message.setProperties(properties);

        var received = mapper.fromJcsmp(message, "topic/sample", 1234L);

        assertEquals("hello", received.body());
        assertEquals("topic/sample", received.destination());
        assertEquals("corr-2", received.correlationId());
        assertEquals("text/plain", received.contentType());
        assertEquals("test", received.properties().get("source"));
        assertEquals(1234L, received.receivedAtMillis());
    }
}
