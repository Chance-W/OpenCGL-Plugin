package com.opencgl.base.hook;

/**
 * TLS 配置
 * 支持双向认证 (mTLS)，可由 Hook 脚本动态提供
 *
 * @author Chance.W
 */
public class TlsConfig {

    private boolean enabled;
    private boolean mutualAuth;  // 双向认证

    // PEM 格式证书
    private String clientCertPath;
    private String clientKeyPath;
    private String clientKeyPassword;
    private String caCertPath;
    private boolean verifyHostname = true;

    // KeyStore 格式
    private String keystorePath;
    private String keystorePassword;
    private String truststorePath;
    private String truststorePassword;

    private TlsConfig() {
    }

    public static TlsConfigBuilder builder() {
        return new TlsConfigBuilder();
    }

    // Getters

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isMutualAuth() {
        return mutualAuth;
    }

    public String getClientCertPath() {
        return clientCertPath;
    }

    public String getClientKeyPath() {
        return clientKeyPath;
    }

    public String getClientKeyPassword() {
        return clientKeyPassword;
    }

    public String getCaCertPath() {
        return caCertPath;
    }

    public boolean isVerifyHostname() {
        return verifyHostname;
    }

    public String getKeystorePath() {
        return keystorePath;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public String getTruststorePath() {
        return truststorePath;
    }

    public String getTruststorePassword() {
        return truststorePassword;
    }

    /**
     * Builder 类
     */
    public static class TlsConfigBuilder {
        private final TlsConfig config = new TlsConfig();

        public TlsConfigBuilder enabled(boolean enabled) {
            config.enabled = enabled;
            return this;
        }

        public TlsConfigBuilder mutualAuth(boolean mutualAuth) {
            config.mutualAuth = mutualAuth;
            return this;
        }

        public TlsConfigBuilder clientCertPath(String path) {
            config.clientCertPath = path;
            return this;
        }

        public TlsConfigBuilder clientKeyPath(String path) {
            config.clientKeyPath = path;
            return this;
        }

        public TlsConfigBuilder clientKeyPassword(String password) {
            config.clientKeyPassword = password;
            return this;
        }

        public TlsConfigBuilder caCertPath(String path) {
            config.caCertPath = path;
            return this;
        }

        public TlsConfigBuilder verifyHostname(boolean verify) {
            config.verifyHostname = verify;
            return this;
        }

        public TlsConfigBuilder keystorePath(String path) {
            config.keystorePath = path;
            return this;
        }

        public TlsConfigBuilder keystorePassword(String password) {
            config.keystorePassword = password;
            return this;
        }

        public TlsConfigBuilder truststorePath(String path) {
            config.truststorePath = path;
            return this;
        }

        public TlsConfigBuilder truststorePassword(String password) {
            config.truststorePassword = password;
            return this;
        }

        public TlsConfig build() {
            return config;
        }
    }
}
