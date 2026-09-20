package com.opencgl.solace.model;

import com.opencgl.base.model.HistoryItem;

/** Persisted snapshot of one Solace send attempt. */
public final class SolaceSendHistoryItem extends HistoryItem {
    private Long nodeId;
    private String destinationType;
    private String destination;
    private String deliveryMode;
    private String contentType;
    private String correlationId;
    private String replyTo;
    private long ttlMillis;
    private String body;
    private String propertiesJson;
    private String result;
    private String correlationKey;
    private long durationMillis;

    public Long getNodeId() { return nodeId; }
    public void setNodeId(Long nodeId) { this.nodeId = nodeId; }
    public String getDestinationType() { return destinationType; }
    public void setDestinationType(String destinationType) { this.destinationType = destinationType; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public String getDeliveryMode() { return deliveryMode; }
    public void setDeliveryMode(String deliveryMode) { this.deliveryMode = deliveryMode; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getReplyTo() { return replyTo; }
    public void setReplyTo(String replyTo) { this.replyTo = replyTo; }
    public long getTtlMillis() { return ttlMillis; }
    public void setTtlMillis(long ttlMillis) { this.ttlMillis = ttlMillis; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getPropertiesJson() { return propertiesJson; }
    public void setPropertiesJson(String propertiesJson) { this.propertiesJson = propertiesJson; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getCorrelationKey() { return correlationKey; }
    public void setCorrelationKey(String correlationKey) { this.correlationKey = correlationKey; }
    public long getDurationMillis() { return durationMillis; }
    public void setDurationMillis(long durationMillis) { this.durationMillis = durationMillis; }
}
