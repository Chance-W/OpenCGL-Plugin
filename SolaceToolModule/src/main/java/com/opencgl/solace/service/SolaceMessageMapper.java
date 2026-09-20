package com.opencgl.solace.service;

import com.opencgl.solace.model.SolaceOutboundMessage;
import com.opencgl.solace.model.SolaceReceivedMessage;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.Destination;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.SDTMap;
import com.solacesystems.jcsmp.TextMessage;
import com.solacesystems.jcsmp.XMLMessage;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SolaceMessageMapper {

    public MappedOutbound toJcsmp(SolaceOutboundMessage source) throws Exception {
        TextMessage message = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
        message.setText(source.body());
        message.setDeliveryMode(source.deliveryMode());
        if (source.deliveryMode() == com.solacesystems.jcsmp.DeliveryMode.PERSISTENT) {
            message.setAckImmediately(true);
        }
        if (source.contentType() != null && !source.contentType().isBlank()) message.setHTTPContentType(source.contentType());
        if (source.contentEncoding() != null && !source.contentEncoding().isBlank()) message.setHTTPContentEncoding(source.contentEncoding());
        if (source.correlationId() != null && !source.correlationId().isBlank()) message.setCorrelationId(source.correlationId());
        if (source.replyTo() != null && !source.replyTo().isBlank()) {
            message.setReplyTo(JCSMPFactory.onlyInstance().createTopic(source.replyTo()));
        }
        if (source.timeToLiveMillis() > 0) message.setTimeToLive(source.timeToLiveMillis());
        if (!source.properties().isEmpty()) {
            SDTMap properties = JCSMPFactory.onlyInstance().createMap();
            for (Map.Entry<String, String> entry : source.properties().entrySet()) {
                properties.putString(entry.getKey(), entry.getValue());
            }
            message.setProperties(properties);
        }
        Destination destination = source.destinationType() == SolaceOutboundMessage.DestinationType.QUEUE
            ? JCSMPFactory.onlyInstance().createQueue(source.destinationName())
            : JCSMPFactory.onlyInstance().createTopic(source.destinationName());
        return new MappedOutbound(message, destination);
    }

    public SolaceReceivedMessage fromJcsmp(XMLMessage message, String destination, long receivedAtMillis) {
        String body;
        if (message instanceof TextMessage textMessage) {
            body = textMessage.getText();
        } else if (message instanceof BytesXMLMessage bytesMessage) {
            byte[] bytes = bytesMessage.getBytes();
            body = bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
        } else {
            body = "";
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        SDTMap sourceProperties = message.getProperties();
        if (sourceProperties != null) {
            for (String key : sourceProperties.keySet()) {
                try { properties.put(key, sourceProperties.get(key)); }
                catch (Exception ignored) { properties.put(key, "<unreadable>"); }
            }
        }
        return new SolaceReceivedMessage(receivedAtMillis, destination, message.getMessageId(),
            message.getCorrelationId(), message.getHTTPContentType(), message.getHTTPContentEncoding(),
            message.getDeliveryMode(), message.getContentLength(), body, properties);
    }

    public record MappedOutbound(XMLMessage message, Destination destination) { }
}
