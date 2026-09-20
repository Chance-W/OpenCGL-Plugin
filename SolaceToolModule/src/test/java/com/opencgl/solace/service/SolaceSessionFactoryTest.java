package com.opencgl.solace.service;

import com.opencgl.solace.model.SolaceConnectionConfig;
import com.solacesystems.jcsmp.JCSMPProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SolaceSessionFactoryTest {

    @Test
    void normalizesReferenceStyleHostAndAppliesReferenceDefaults() {
        var config = new SolaceConnectionConfig();
        config.setHost("tcp://broker.example");
        config.setUsername("developer");
        config.setPassword("secret");

        JCSMPProperties properties = new SolaceSessionFactory().toProperties(config);

        assertEquals("broker.example:55555", properties.getStringProperty(JCSMPProperties.HOST));
        assertEquals("default", properties.getStringProperty(JCSMPProperties.VPN_NAME));
    }

    @Test
    void mapsConnectionAndTlsSettingsToJcsmpProperties() {
        var config = new SolaceConnectionConfig();
        config.setHost("tcps://broker.example:55443");
        config.setMessageVpn("payments");
        config.setUsername("developer");
        config.setPassword("secret");
        config.setClientName("OpenCGL-test");
        config.setConnectTimeoutMillis(7_000);
        config.setReconnectRetries(8);
        config.setReconnectRetryWaitMillis(1_500);
        config.setTlsEnabled(true);
        config.setValidateCertificate(false);
        config.setTrustStorePath("/tmp/truststore.jks");
        config.setTrustStorePassword("changeit");

        JCSMPProperties properties = new SolaceSessionFactory().toProperties(config);

        assertEquals(config.getHost(), properties.getStringProperty(JCSMPProperties.HOST));
        assertEquals(config.getMessageVpn(), properties.getStringProperty(JCSMPProperties.VPN_NAME));
        assertEquals(config.getUsername(), properties.getStringProperty(JCSMPProperties.USERNAME));
        assertEquals(config.getPassword(), properties.getStringProperty(JCSMPProperties.PASSWORD));
        assertEquals(config.getClientName(), properties.getStringProperty(JCSMPProperties.CLIENT_NAME));
        assertEquals(7_000, properties.getIntegerProperty(JCSMPProperties.CLIENT_CHANNEL_PROPERTIES_CONNECT_TIMEOUT_IN_MILLIS));
        assertEquals(8, properties.getIntegerProperty(JCSMPProperties.CLIENT_CHANNEL_PROPERTIES_RECONNECT_RETRIES));
        assertEquals(1_500, properties.getIntegerProperty(JCSMPProperties.CLIENT_CHANNEL_PROPERTIES_RECONNECT_RETRY_WAIT_IN_MILLIS));
        assertEquals(false, properties.getBooleanProperty(JCSMPProperties.SSL_VALIDATE_CERTIFICATE));
        assertEquals("/tmp/truststore.jks", properties.getStringProperty(JCSMPProperties.SSL_TRUST_STORE));
        assertEquals("changeit", properties.getStringProperty(JCSMPProperties.SSL_TRUST_STORE_PASSWORD));
    }
}
