package com.opencgl.graphql.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * GraphQL 服务
 * 支持 GraphQL 查询、变更和订阅
 *
 * @author OpenCGL
 */
public class GraphQLService implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(GraphQLService.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final MediaType JSON = MediaType.parse("application/json");

    private OkHttpClient client;
    private String endpoint;

    public GraphQLService() {
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    /**
     * 设置 GraphQL 端点
     */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * 执行 GraphQL 查询
     */
    public GraphQLResult execute(String query, String variables, Map<String, String> headers) throws IOException {
        if (endpoint == null || endpoint.isEmpty()) {
            throw new IllegalStateException("未设置 GraphQL 端点");
        }

        // 构建请求体
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("query", query);
        
        if (variables != null && !variables.isEmpty()) {
            try {
                JsonNode varsNode = mapper.readTree(variables);
                requestBody.set("variables", varsNode);
            } catch (Exception e) {
                logger.warn("解析 variables 失败", e);
            }
        }

        String jsonBody = mapper.writeValueAsString(requestBody);

        // 构建 HTTP 请求
        Request.Builder reqBuilder = new Request.Builder()
            .url(endpoint)
            .post(RequestBody.create(jsonBody, JSON))
            .addHeader("Content-Type", "application/json");

        if (headers != null) {
            headers.forEach(reqBuilder::addHeader);
        }

        long startTime = System.currentTimeMillis();

        // 执行请求
        try (Response response = client.newCall(reqBuilder.build()).execute()) {
            long elapsed = System.currentTimeMillis() - startTime;
            
            String responseBody = response.body() != null ? response.body().string() : "";
            
            // 格式化 JSON
            String formattedJson;
            try {
                JsonNode jsonNode = mapper.readTree(responseBody);
                formattedJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
            } catch (Exception e) {
                formattedJson = responseBody;
            }

            return new GraphQLResult(
                response.code(),
                elapsed,
                formattedJson,
                response.isSuccessful()
            );
        }
    }

    /**
     * 获取 Schema (Introspection)
     */
    public String introspect(Map<String, String> headers) throws IOException {
        String introspectionQuery = """
            query IntrospectionQuery {
              __schema {
                types {
                  name
                  kind
                  description
                  fields {
                    name
                    type { name kind }
                  }
                }
                queryType { name }
                mutationType { name }
              }
            }
            """;
        
        GraphQLResult result = execute(introspectionQuery, null, headers);
        return result.body();
    }

    @Override
    public void close() {
        cleanup(() -> client.dispatcher().cancelAll());
        cleanup(() -> client.dispatcher().executorService().shutdown());
        cleanup(() -> client.connectionPool().evictAll());
        cleanup(() -> {
            if (client.cache() != null) {
                try {
                    client.cache().close();
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
    }

    private void cleanup(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            logger.warn("Failed to close GraphQL HTTP resource", e);
        }
    }

    public record GraphQLResult(int statusCode, long elapsed, String body, boolean success) {}
}
