package com.opencgl.solace.service;

/** Immutable listener settings used by the UI and runtime adapter. */
public record SolaceListenerConfig(DestinationType destinationType, String destinationName,
                                   boolean clientAcknowledge) {
    public enum DestinationType { TOPIC, QUEUE }

    public SolaceListenerConfig {
        destinationType = destinationType == null ? DestinationType.TOPIC : destinationType;
        destinationName = destinationName == null ? "" : destinationName.trim();
    }
}
