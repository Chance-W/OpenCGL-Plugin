package com.opencgl.http.service;

import com.opencgl.http.model.HttpRequestModel;
import com.opencgl.http.model.HttpResponseModel;
import com.opencgl.http.model.ProxyConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * HTTP客户端服务
 */
public class HttpClientService {
    private static final Logger logger = LoggerFactory.getLogger(HttpClientService.class);

    private javax.net.ssl.SSLContext trustAllSslContext;
    private final ProxyConfigService proxyConfigService;
    private final ExecutorService httpExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "http-debugger-client");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean disposed;

    public HttpClientService() {
        this.proxyConfigService = new ProxyConfigService();
        try {
            // Initialize Trust-All SSL Context
            trustAllSslContext = SSLContext.getInstance("TLS");
            trustAllSslContext.init(null, new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
            }, new SecureRandom());
        }
        catch (Exception e) {
            logger.error("Failed to initialize SSL Context", e);
        }
    }

    public ProxyConfigService getProxyConfigService() {
        return proxyConfigService;
    }

    /**
     * 发送HTTP请求
     */
    public HttpResponseModel sendRequest(HttpRequestModel requestModel) {
        if (disposed) throw new IllegalStateException("HTTP client service is disposed");
        HttpResponseModel responseModel = new HttpResponseModel();
        long startTime = System.currentTimeMillis();

        try {
            // 1. Build Client dynamically
            HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .executor(httpExecutor)
                .connectTimeout(Duration.ofSeconds(requestModel.getTimeout()))
                .followRedirects(requestModel.isFollowRedirects() ? HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER)
                .proxy(new ProxySelector() {
                    @Override
                    public List<Proxy> select(URI uri) {
                        ProxyConfig config = proxyConfigService.getConfig();
                        if (config != null && config.isEnabled()) {
                            Proxy.Type type = "SOCKS".equalsIgnoreCase(config.getType()) ?
                                Proxy.Type.SOCKS : Proxy.Type.HTTP;
                            return List.of(new Proxy(type,
                                new InetSocketAddress(config.getHost(), config.getPort())));
                        }
                        return List.of(Proxy.NO_PROXY);
                    }

                    @Override
                    public void connectFailed(URI uri, SocketAddress sa, java.io.IOException ioe) {
                        logger.error("Proxy connection failed to {},{}", sa, ioe);
                    }
                });

            // SSL Configuration (mTLS & Custom CA)
            javax.net.ssl.SSLContext sslContext = null;

            // Check if we need custom SSL Context
            boolean hasClientCert = isNotEmpty(requestModel.getClientCertPath());
            boolean hasServerCert = isNotEmpty(requestModel.getServerCertPath());

            if (hasClientCert || hasServerCert) {
                try {
                    javax.net.ssl.KeyManager[] keyManagers = null;
                    javax.net.ssl.TrustManager[] trustManagers = null;

                    // 1. Client Certificate (KeyManager)
                    if (hasClientCert) {
                        KeyStore clientStore =
                            KeyStore.getInstance("PKCS12"); // Try PKCS12 first (supports JKS too usually or separate)
                        // Note: Default Java KeyStore usually handles JKS. PKCS12 for .p12
                        String path = requestModel.getClientCertPath();
                        String pass = requestModel.getClientCertPass() != null ? requestModel.getClientCertPass() : "";

                        if (path.toLowerCase().endsWith(".jks")) {
                            clientStore = java.security.KeyStore.getInstance("JKS");
                        }

                        try (java.io.FileInputStream fis = new java.io.FileInputStream(path)) {
                            clientStore.load(fis, pass.toCharArray());
                        }

                        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                        kmf.init(clientStore, pass.toCharArray());
                        keyManagers = kmf.getKeyManagers();
                    }

                    // 2. Server CA (TrustManager)
                    if (hasServerCert) {
                        KeyStore trustStore;
                        String path = requestModel.getServerCertPath();
                        String pass = requestModel.getServerCertPass() != null ? requestModel.getServerCertPass() : "";

                        if (path.toLowerCase().endsWith(".cer") || path.toLowerCase().endsWith(".crt") || path.toLowerCase().endsWith(".pem")) {
                            // Cert file
                            CertificateFactory cf = CertificateFactory.getInstance("X.509");
                            try (FileInputStream fis = new FileInputStream(path)) {
                                Certificate ca = cf.generateCertificate(fis);
                                trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                                trustStore.load(null, null);
                                trustStore.setCertificateEntry("custom-ca", ca);
                            }
                        }
                        else {
                            // JKS or P12
                            trustStore = KeyStore.getInstance(path.toLowerCase().endsWith(".p12") ? "PKCS12" : "JKS");
                            try (FileInputStream fis = new FileInputStream(path)) {
                                trustStore.load(fis, pass.toCharArray());
                            }
                        }

                        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                        tmf.init(trustStore);
                        trustManagers = tmf.getTrustManagers();
                    }
                    else if (!requestModel.isSslVerification()) {
                        // Fallback to Trust All if no specific CA provided and Verification is OFF
                        trustManagers = new TrustManager[]{
                            new X509TrustManager() {
                                public X509Certificate[] getAcceptedIssuers() {
                                    return null;
                                }

                                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                                }

                                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                                }
                            }
                        };
                    }
                    // else: default system trust (trustManagers = null)

                    sslContext = SSLContext.getInstance("TLS");
                    sslContext.init(keyManagers, trustManagers, new SecureRandom());

                }
                catch (Exception e) {
                    logger.error("Failed to initialize Custom SSL Context", e);
                    throw new IOException("SSL Config Error: " + e.getMessage(), e);
                }
            }
            else if (!requestModel.isSslVerification() && trustAllSslContext != null) {
                // Legacy Trust All Path
                sslContext = trustAllSslContext;
            }

            if (sslContext != null) {
                clientBuilder.sslContext(sslContext);
            }

            HttpClient client = clientBuilder.build();
            // 构建完整URL（包含query params）
            String fullUrl = buildUrl(requestModel.getUrl(), requestModel.getParams());

            // 构建请求体
            HttpRequest.BodyPublisher bodyPublisher = buildBody(requestModel);

            // 构建请求
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .method(requestModel.getMethod(), bodyPublisher)
                .timeout(Duration.ofSeconds(30));
            // Use configured timeout, default was 30 globally, but client has it?
            // Wait, client has connectTimeout. request has timeout (read timeout).
            // We should use requestModel.getTimeout() here too?
            // Currently hardcoded 30 in builder line 103 originally.
            // Let's use requestModel.getTimeout() for both if possible or keep consistent.
            requestBuilder.timeout(Duration.ofSeconds(requestModel.getTimeout()));

            // 若用户未手动指定 Content-Type，根据 bodyType 自动补全。
            // 不自动补全会导致 Java HttpClient 发出不含 Content-Type 的请求，
            // Spring 等框架收到后默认视为 application/octet-stream，触发 "not supported" 错误。
            boolean userSetContentType = requestModel.getHeaders().keySet().stream()
                .anyMatch(k -> "content-type".equalsIgnoreCase(k));
            if (!userSetContentType) {
                String bodyForCheck = requestModel.getBody();
                if (bodyForCheck != null && !bodyForCheck.isEmpty()) {
                    String autoContentType = inferContentType(requestModel.getBodyType());
                    requestBuilder.header("Content-Type", autoContentType);
                }
            }

            // 添加请求头（用户手动设置的，会覆盖上面自动补全的同名头）
            for (Map.Entry<String, String> header : requestModel.getHeaders().entrySet()) {
                if (header.getKey() != null && !header.getKey().isEmpty()) {
                    requestBuilder.setHeader(header.getKey(), header.getValue());
                }
            }

            HttpRequest request = requestBuilder.build();

            // 发送请求
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // 计算响应时间
            long responseTime = System.currentTimeMillis() - startTime;

            // 填充响应模型
            responseModel.setStatusCode(response.statusCode());
            responseModel.setBody(response.body());
            responseModel.setResponseTime(responseTime);
            responseModel.setContentLength(response.body().length());

            // 提取响应头
            Map<String, String> responseHeaders = response.headers().map().entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> String.join(", ", e.getValue())
                ));
            responseModel.setHeaders(responseHeaders);

            // 设置状态消息
            responseModel.setStatusMessage(getStatusMessage(response.statusCode()));

            logger.info("请求成功: {} {} - {} ({}ms)",
                requestModel.getMethod(), fullUrl, response.statusCode(), responseTime);

        }
        catch (IOException | InterruptedException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            responseModel.setResponseTime(responseTime);
            responseModel.setStatusMessage("Request Failed");
            // Show error details in body so user knows why it failed
            // Show error details in body so user knows why it failed
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));

            String errorDetail = "Error: " + e.getMessage();
            if (e.getCause() != null) {
                errorDetail += "\nCause: " + e.getCause().getMessage();
            }
            errorDetail += "\n\nStack Trace:\n" + sw.toString();

            responseModel.setBody(errorDetail);
            responseModel.setHeaders(Collections.emptyMap());
            logger.error("请求失败", e);
        }

        return responseModel;
    }

    public void close() {
        if (disposed) return;
        disposed = true;
        try {
            httpExecutor.shutdownNow();
        } catch (Exception ignored) {
        }
    }

    private boolean isNotEmpty(String str) {
        return str != null && !str.trim().isEmpty();
    }

    /**
     * 构建完整URL（包含query params）
     */
    private String buildUrl(String baseUrl, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return baseUrl;
        }

        String queryString = params.entrySet().stream()
            .filter(e -> e.getKey() != null && !e.getKey().isEmpty())
            .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" +
                URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));

        if (queryString.isEmpty()) {
            return baseUrl;
        }

        return baseUrl + (baseUrl.contains("?") ? "&" : "?") + queryString;
    }

    /**
     * 构建请求体
     */
    private HttpRequest.BodyPublisher buildBody(HttpRequestModel requestModel) {
        String method = requestModel.getMethod();

        // GET, HEAD, DELETE通常没有body
        if ("GET".equals(method) || "HEAD".equals(method)) {
            return HttpRequest.BodyPublishers.noBody();
        }

        String body = requestModel.getBody();
        if (body == null || body.isEmpty()) {
            return HttpRequest.BodyPublishers.noBody();
        }

        return HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
    }

    /**
     * 根据 bodyType 推断对应的 Content-Type 值。
     * 当用户未在 Headers 中手动设置 Content-Type 时，由此方法自动补全，
     * 避免 Spring / Tomcat 等框架将无 Content-Type 的有体请求解析为 application/octet-stream。
     */
    private String inferContentType(String bodyType) {
        if (bodyType == null) return "application/json; charset=utf-8";
        return switch (bodyType.toUpperCase()) {
            case "JSON" -> "application/json; charset=utf-8";
            case "XML" -> "application/xml; charset=utf-8";
            case "FORM" -> "application/x-www-form-urlencoded";
            case "RAW" -> "text/plain; charset=utf-8";
            default -> "application/json; charset=utf-8";
        };
    }

    /**
     * 获取HTTP状态消息
     */
    private String getStatusMessage(int statusCode) {
        return switch (statusCode) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 304 -> "Not Modified";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            default -> "Unknown";
        };
    }
}
