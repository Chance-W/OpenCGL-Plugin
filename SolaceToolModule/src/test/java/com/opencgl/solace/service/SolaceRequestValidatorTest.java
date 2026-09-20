package com.opencgl.solace.service;

import com.opencgl.solace.model.SolaceConnectionConfig;
import com.opencgl.solace.model.SolaceOutboundMessage;
import com.solacesystems.jcsmp.DeliveryMode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolaceRequestValidatorTest {

    @Test
    void sendReportsOnlyMissingRequiredConnectionAndMessageFields() {
        SolaceConnectionConfig connection = new SolaceConnectionConfig();
        SolaceOutboundMessage message = new SolaceOutboundMessage(
            SolaceOutboundMessage.DestinationType.TOPIC, " ", "", "application/json", "UTF-8",
            null, null, DeliveryMode.PERSISTENT, 0, Map.of());

        assertEquals(java.util.List.of(
            "连接地址（Host）", "用户名", "密码", "Topic / Queue", "消息正文"),
            SolaceRequestValidator.validateSend(connection, message));
    }

    @Test
    void listenerAcceptsCompleteConnectionAndDestination() {
        SolaceConnectionConfig connection = completeConnection();
        SolaceListenerConfig listener = new SolaceListenerConfig(
            SolaceListenerConfig.DestinationType.QUEUE, "orders", true);

        assertTrue(SolaceRequestValidator.validateListener(connection, listener).isEmpty());
    }

    @Test
    void certificateValidationRequiresTrustStoreWhenTlsValidationIsEnabled() {
        SolaceConnectionConfig connection = completeConnection();
        connection.setTlsEnabled(true);
        connection.setValidateCertificate(true);

        assertEquals(java.util.List.of("Trust Store"),
            SolaceRequestValidator.validateConnection(connection));
    }

    private SolaceConnectionConfig completeConnection() {
        SolaceConnectionConfig connection = new SolaceConnectionConfig();
        connection.setHost("tcp://localhost:55555");
        connection.setMessageVpn("default");
        connection.setUsername("user");
        connection.setPassword("secret");
        return connection;
    }
}
