package com.opencgl.base.hook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * TLS 上下文工厂
 * 根据 TlsConfig 创建 SSLContext，支持双向认证
 *
 * @author Chance.W
 */
public class TlsContextFactory {

    private static final Logger logger = LoggerFactory.getLogger(TlsContextFactory.class);

    /**
     * 根据配置创建 SSLContext
     *
     * @param config TLS 配置
     * @return SSLContext
     * @throws Exception 创建失败
     */
    public static SSLContext create(TlsConfig config) throws Exception {
        if (config == null || !config.isEnabled()) {
            return SSLContext.getDefault();
        }

        KeyManager[] keyManagers = null;
        TrustManager[] trustManagers = null;

        // 1. 加载客户端证书 (用于 mTLS)
        if (config.isMutualAuth()) {
            keyManagers = loadKeyManagers(config);
        }

        // 2. 加载 CA 证书 (用于验证服务端)
        trustManagers = loadTrustManagers(config);

        // 3. 创建 SSLContext
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, trustManagers, new SecureRandom());

        logger.info("已创建 SSLContext, mTLS={}", config.isMutualAuth());
        return sslContext;
    }

    /**
     * 加载客户端密钥管理器 (用于双向认证)
     */
    private static KeyManager[] loadKeyManagers(TlsConfig config) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");

        // 优先使用 KeyStore
        if (config.getKeystorePath() != null && !config.getKeystorePath().isEmpty()) {
            char[] password = config.getKeystorePassword() != null
                    ? config.getKeystorePassword().toCharArray()
                    : new char[0];
            try (InputStream is = new FileInputStream(config.getKeystorePath())) {
                keyStore.load(is, password);
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, password);
            return kmf.getKeyManagers();
        }

        // 使用 PEM 证书 (需要转换，这里简化处理)
        if (config.getClientCertPath() != null && config.getClientKeyPath() != null) {
            logger.warn("PEM 格式证书需要额外处理，建议使用 PKCS12 格式 (.p12)");
            // TODO: 实现 PEM 到 KeyStore 的转换
        }

        return null;
    }

    /**
     * 加载信任管理器 (用于验证服务端证书)
     */
    private static TrustManager[] loadTrustManagers(TlsConfig config) throws Exception {
        // 使用 TrustStore
        if (config.getTruststorePath() != null && !config.getTruststorePath().isEmpty()) {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            char[] password = config.getTruststorePassword() != null
                    ? config.getTruststorePassword().toCharArray()
                    : new char[0];
            try (InputStream is = new FileInputStream(config.getTruststorePath())) {
                trustStore.load(is, password);
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            return tmf.getTrustManagers();
        }

        // 使用 CA 证书 PEM
        if (config.getCaCertPath() != null && !config.getCaCertPath().isEmpty()) {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            try (InputStream is = Files.newInputStream(Paths.get(config.getCaCertPath()))) {
                Certificate cert = cf.generateCertificate(is);
                trustStore.setCertificateEntry("ca", cert);
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            return tmf.getTrustManagers();
        }

        // 返回默认信任管理器
        return null;
    }

    /**
     * 创建不验证证书的 SSLContext
     * 
     * ⚠️ 安全警告：此方法会完全跳过证书验证，存在严重安全风险！
     * - 可能遭受中间人攻击
     * - 可能导致敏感数据泄露
     * - 仅用于开发/测试环境
     * 
     * @deprecated 生产环境请使用 {@link #create(TlsConfig)} 方法
     * @throws SecurityException 如果在生产环境调用此方法
     */
    @Deprecated
    public static SSLContext createInsecure() throws Exception {
        // 检查是否为开发环境（通过系统属性控制）
        String devMode = System.getProperty("opencgl.dev.mode", "false");
        if (!"true".equalsIgnoreCase(devMode)) {
            logger.error("尝试在非开发模式下使用不安全的 SSLContext！设置 -Dopencgl.dev.mode=true 以启用");
            throw new SecurityException("createInsecure() 仅允许在开发模式下使用。请设置系统属性 opencgl.dev.mode=true");
        }
        
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        // 警告：跳过客户端证书验证
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        // 警告：跳过服务端证书验证
                    }
                }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new SecureRandom());
        logger.warn("⚠️ 安全警告：使用不安全的 SSLContext，证书验证已跳过！仅限开发环境使用！");
        return sslContext;
    }
}
