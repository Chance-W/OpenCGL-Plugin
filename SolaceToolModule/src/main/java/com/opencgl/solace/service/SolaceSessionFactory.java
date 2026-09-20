package com.opencgl.solace.service;

import com.opencgl.solace.model.SolaceConnectionConfig;
import com.solacesystems.jcsmp.InvalidPropertiesException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;

import java.util.Objects;

/** Converts UI-neutral connection settings into a JCSMP session. */
public final class SolaceSessionFactory {

    public JCSMPProperties toProperties(SolaceConnectionConfig config) {
        Objects.requireNonNull(config, "config");
        JCSMPProperties properties = new JCSMPProperties();
        put(properties, JCSMPProperties.HOST, normalizeHost(config.getHost()));
        properties.setProperty(JCSMPProperties.VPN_NAME, defaultIfBlank(config.getMessageVpn(), "default"));
        put(properties, JCSMPProperties.USERNAME, config.getUsername());
        put(properties, JCSMPProperties.PASSWORD, config.getPassword());
        put(properties, JCSMPProperties.CLIENT_NAME, config.getClientName());
        properties.setIntegerProperty(JCSMPProperties.CLIENT_CHANNEL_PROPERTIES_CONNECT_TIMEOUT_IN_MILLIS,
            positive(config.getConnectTimeoutMillis(), 10_000));
        properties.setIntegerProperty(JCSMPProperties.CLIENT_CHANNEL_PROPERTIES_RECONNECT_RETRIES,
            Math.max(0, config.getReconnectRetries()));
        properties.setIntegerProperty(JCSMPProperties.CLIENT_CHANNEL_PROPERTIES_RECONNECT_RETRY_WAIT_IN_MILLIS,
            positive(config.getReconnectRetryWaitMillis(), 3_000));
        if (config.isTlsEnabled()) {
            properties.setBooleanProperty(JCSMPProperties.SSL_VALIDATE_CERTIFICATE, config.isValidateCertificate());
            put(properties, JCSMPProperties.SSL_TRUST_STORE, config.getTrustStorePath());
            put(properties, JCSMPProperties.SSL_TRUST_STORE_PASSWORD, config.getTrustStorePassword());
        }
        return properties;
    }

    public JCSMPSession create(SolaceConnectionConfig config) throws InvalidPropertiesException {
        return JCSMPFactory.onlyInstance().createSession(toProperties(config));
    }

    private static void put(JCSMPProperties properties, String key, String value) {
        if (value != null && !value.isBlank()) properties.setProperty(key, value);
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    static String normalizeHost(String rawHost) {
        if (rawHost == null) return null;
        String host = rawHost.trim();
        if (host.startsWith("tcp://")) host = host.substring("tcp://".length());
        if (host.isBlank() || host.startsWith("tcps://")) return host;
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) {
            try {
                Integer.parseInt(host.substring(colon + 1));
                return host;
            } catch (NumberFormatException ignored) {
                // Match the web implementation: malformed suffix falls back to the form/default port.
            }
        }
        return host + ":55555";
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
