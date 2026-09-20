package com.opencgl.solace.model;

import com.solacesystems.jcsmp.DeliveryMode;

import java.util.Map;

public record SolaceReceivedMessage(
    long receivedAtMillis,
    String destination,
    String messageId,
    String correlationId,
    String contentType,
    String contentEncoding,
    DeliveryMode deliveryMode,
    int size,
    String body,
    Map<String, Object> properties
) {
    public SolaceReceivedMessage {
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }
}
