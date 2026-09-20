package com.opencgl.solace.variable;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SolaceVariableResolverTest {

    private final SolaceVariableResolver resolver = new SolaceVariableResolver(
        name -> Map.of("BROKER_HOST", "env-host").get(name),
        name -> Map.of("app.region", "cn-east").get(name),
        Clock.fixed(Instant.parse("2026-09-16T04:30:20Z"), ZoneOffset.UTC)
    );

    @Test
    void resolvesScopesFromMostSpecificToLeastSpecific() {
        var scope = new SolaceVariableResolver.VariableScope(
            Map.of("host", "environment", "shared", "environment"),
            Map.of("host", "connection", "shared", "connection"),
            Map.of("host", "node")
        );

        var result = resolver.resolve("${host}/${shared}", scope);

        assertEquals("node/connection", result.value());
        assertTrue(result.unresolvedVariables().isEmpty());
    }

    @Test
    void resolvesDynamicEnvironmentAndSystemVariables() {
        var result = resolver.resolve(
            "${UUID_SIMPLE}|${TIMESTAMP}|${TIMESTAMP_S}|${Random6}|${yyyyMMddHHmmss}|${env:BROKER_HOST}|${sys:app.region}",
            SolaceVariableResolver.VariableScope.empty()
        );

        String[] values = result.value().split("\\|");
        assertEquals(7, values.length);
        assertTrue(values[0].matches("[a-f0-9]{32}"));
        assertEquals("1789533020000", values[1]);
        assertEquals("1789533020", values[2]);
        assertTrue(values[3].matches("[1-9][0-9]{5}"));
        assertEquals("20260916043020", values[4]);
        assertEquals("env-host", values[5]);
        assertEquals("cn-east", values[6]);
    }

    @Test
    void reportsAndPreservesUnresolvedVariables() {
        var result = resolver.resolve("tcp://${missing}:55555/${missing}", SolaceVariableResolver.VariableScope.empty());

        assertEquals("tcp://${missing}:55555/${missing}", result.value());
        assertEquals(java.util.Set.of("missing"), result.unresolvedVariables());
    }

    @Test
    void masksSecretValuesInPreview() {
        assertEquals("******", resolver.mask("password", "secret"));
        assertEquals("******", resolver.mask("apiToken", "abcdef"));
        assertEquals("******", resolver.mask("client_secret", "abcdef"));
        assertEquals("visible", resolver.mask("messageVpn", "visible"));
        assertEquals("", resolver.mask("password", ""));
    }
}
