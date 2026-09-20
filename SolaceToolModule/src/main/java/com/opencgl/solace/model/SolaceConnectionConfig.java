package com.opencgl.solace.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Persisted, UI-neutral Solace connection settings. */
public class SolaceConnectionConfig {
    private String host;
    private String messageVpn;
    private String username;
    private String password;
    private boolean rememberPassword;
    private String clientName;
    private int connectTimeoutMillis = 10_000;
    private int reconnectRetries = 3;
    private int reconnectRetryWaitMillis = 3_000;
    private boolean tlsEnabled;
    private boolean validateCertificate = true;
    private String trustStorePath;
    private String trustStorePassword;
    private Map<String, String> variables = new LinkedHashMap<>();

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public String getMessageVpn() { return messageVpn; }
    public void setMessageVpn(String messageVpn) { this.messageVpn = messageVpn; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public boolean isRememberPassword() { return rememberPassword; }
    public void setRememberPassword(boolean rememberPassword) { this.rememberPassword = rememberPassword; }
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
    public int getConnectTimeoutMillis() { return connectTimeoutMillis; }
    public void setConnectTimeoutMillis(int connectTimeoutMillis) { this.connectTimeoutMillis = connectTimeoutMillis; }
    public int getReconnectRetries() { return reconnectRetries; }
    public void setReconnectRetries(int reconnectRetries) { this.reconnectRetries = reconnectRetries; }
    public int getReconnectRetryWaitMillis() { return reconnectRetryWaitMillis; }
    public void setReconnectRetryWaitMillis(int reconnectRetryWaitMillis) { this.reconnectRetryWaitMillis = reconnectRetryWaitMillis; }
    public boolean isTlsEnabled() { return tlsEnabled; }
    public void setTlsEnabled(boolean tlsEnabled) { this.tlsEnabled = tlsEnabled; }
    public boolean isValidateCertificate() { return validateCertificate; }
    public void setValidateCertificate(boolean validateCertificate) { this.validateCertificate = validateCertificate; }
    public String getTrustStorePath() { return trustStorePath; }
    public void setTrustStorePath(String trustStorePath) { this.trustStorePath = trustStorePath; }
    public String getTrustStorePassword() { return trustStorePassword; }
    public void setTrustStorePassword(String trustStorePassword) { this.trustStorePassword = trustStorePassword; }
    public Map<String, String> getVariables() { return variables; }
    public void setVariables(Map<String, String> variables) {
        this.variables = variables == null ? new LinkedHashMap<>() : new LinkedHashMap<>(variables);
    }
}
