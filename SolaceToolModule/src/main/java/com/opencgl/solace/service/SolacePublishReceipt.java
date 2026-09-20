package com.opencgl.solace.service;

/** Result returned after a Solace publish operation has completed. */
public record SolacePublishReceipt(
    Status status,
    String correlationKey,
    long completedAtMillis,
    String message
) {
    public enum Status {
        /** Guaranteed message was acknowledged by the broker. */
        BROKER_ACK,
        /** Direct message was accepted by the client API; Direct delivery has no broker ACK. */
        DIRECT_ACCEPTED
    }
}
