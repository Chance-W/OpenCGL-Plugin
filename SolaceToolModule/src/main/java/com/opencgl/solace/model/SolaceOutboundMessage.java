package com.opencgl.solace.model;

import com.solacesystems.jcsmp.DeliveryMode;

import java.util.Map;

public record SolaceOutboundMessage(
    DestinationType destinationType,
    String destinationName,
    String body,
    String contentType,
    String contentEncoding,
    String correlationId,
    String replyTo,
    DeliveryMode deliveryMode,
    long timeToLiveMillis,
    Map<String, String> properties
) {
    public enum DestinationType { TOPIC, QUEUE }

    public SolaceOutboundMessage {
        destinationType = destinationType == null ? DestinationType.TOPIC : destinationType;
        body = body == null ? "" : body;
        deliveryMode = deliveryMode == null ? DeliveryMode.DIRECT : deliveryMode;
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }
}
