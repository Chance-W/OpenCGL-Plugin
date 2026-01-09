package com.opencgl.elasticsearch.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.*;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;

/**
 * Elasticsearch 服务
 * 支持连接、索引管理、文档搜索、聚合分析
 *
 * @author OpenCGL
 */
public class ElasticsearchService implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private RestClient restClient;
    private ElasticsearchTransport transport;
    private ElasticsearchClient client;

    /**
     * 连接到 Elasticsearch
     */
    public void connect(String host, int port, String username, String password) {
        close();
        
        HttpHost httpHost = new HttpHost(host, port, "http");
        RestClientBuilder builder = RestClient.builder(httpHost);
        
        if (username != null && !username.isEmpty()) {
            final CredentialsProvider credsProv = new BasicCredentialsProvider();
            credsProv.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
            builder.setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
                @Override
                public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
                    return httpClientBuilder.setDefaultCredentialsProvider(credsProv);
                }
            });
        }
        
        restClient = builder.build();
        transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        client = new ElasticsearchClient(transport);
    }

    /**
     * 测试连接
     */
    public boolean testConnection() {
        if (client == null) return false;
        try {
            client.info();
            return true;
        } catch (Exception e) {
            logger.error("Elasticsearch 连接测试失败", e);
            return false;
        }
    }

    /**
     * 获取集群信息
     */
    public String getClusterInfo() throws IOException {
        var info = client.info();
        return String.format("Cluster: %s\nVersion: %s\nNode: %s",
            info.clusterName(), info.version().number(), info.name());
    }

    // ==================== 索引管理 ====================

    /**
     * 获取索引列表
     */
    public List<IndexInfo> listIndices() throws IOException {
        IndicesResponse response = client.cat().indices();
        List<IndexInfo> indices = new ArrayList<>();
        for (var record : response.valueBody()) {
            indices.add(new IndexInfo(
                record.index(),
                record.health(),
                record.status(),
                record.docsCount(),
                record.storeSize()
            ));
        }
        return indices;
    }

    /**
     * 创建索引
     */
    public void createIndex(String indexName, String mappingJson) throws IOException {
        CreateIndexRequest.Builder builder = new CreateIndexRequest.Builder().index(indexName);
        
        if (mappingJson != null && !mappingJson.isEmpty()) {
            builder.withJson(new StringReader("{\"mappings\":" + mappingJson + "}"));
        }
        
        client.indices().create(builder.build());
    }

    /**
     * 删除索引
     */
    public void deleteIndex(String indexName) throws IOException {
        client.indices().delete(d -> d.index(indexName));
    }

    /**
     * 获取索引映射
     */
    public String getMapping(String indexName) throws IOException {
        GetMappingResponse response = client.indices().getMapping(m -> m.index(indexName));
        var mapping = response.get(indexName);
        if (mapping != null) {
            return objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(mapping.mappings());
        }
        return "{}";
    }

    // ==================== 文档操作 ====================

    /**
     * 搜索文档
     */
    public SearchResult search(String indexName, String queryJson, int size) throws IOException {
        SearchRequest.Builder builder = new SearchRequest.Builder()
            .index(indexName)
            .size(size);
        
        if (queryJson != null && !queryJson.isEmpty()) {
            builder.withJson(new StringReader("{\"query\":" + queryJson + "}"));
        }
        
        SearchResponse<ObjectNode> response = client.search(builder.build(), ObjectNode.class);
        
        List<String> docs = new ArrayList<>();
        for (Hit<ObjectNode> hit : response.hits().hits()) {
            ObjectNode source = hit.source();
            if (source != null) {
                ObjectNode doc = objectMapper.createObjectNode();
                doc.put("_id", hit.id());
                doc.put("_score", hit.score());
                doc.setAll(source);
                docs.add(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(doc));
            }
        }
        
        long total = response.hits().total() != null ? response.hits().total().value() : 0;
        long took = response.took();
        
        return new SearchResult(total, took, docs);
    }

    /**
     * 索引文档
     */
    public String indexDocument(String indexName, String id, String documentJson) throws IOException {
        IndexRequest.Builder<ObjectNode> builder = new IndexRequest.Builder<ObjectNode>()
            .index(indexName)
            .document(objectMapper.readValue(documentJson, ObjectNode.class));
        
        if (id != null && !id.isEmpty()) {
            builder.id(id);
        }
        
        IndexResponse response = client.index(builder.build());
        return response.id();
    }

    /**
     * 删除文档
     */
    public boolean deleteDocument(String indexName, String id) throws IOException {
        DeleteResponse response = client.delete(d -> d.index(indexName).id(id));
        return "deleted".equals(response.result().jsonValue());
    }

    /**
     * 批量删除
     */
    public long deleteByQuery(String indexName, String queryJson) throws IOException {
        DeleteByQueryRequest.Builder builder = new DeleteByQueryRequest.Builder()
            .index(indexName);
        
        if (queryJson != null && !queryJson.isEmpty()) {
            builder.withJson(new StringReader("{\"query\":" + queryJson + "}"));
        }
        
        DeleteByQueryResponse response = client.deleteByQuery(builder.build());
        return response.deleted() != null ? response.deleted() : 0;
    }

    @Override
    public void close() {
        ElasticsearchTransport transportToClose = transport;
        RestClient restClientToClose = restClient;
        client = null;
        transport = null;
        restClient = null;

        if (transportToClose != null) {
            try {
                transportToClose.close();
            } catch (Exception e) {
                logger.error("关闭 Elasticsearch transport 失败", e);
            }
        }
        if (restClientToClose != null) {
        try {
                restClientToClose.close();
        } catch (Exception e) {
                logger.error("关闭 Elasticsearch REST client 失败", e);
            }
        }
    }

    public record IndexInfo(String name, String health, String status, String docsCount, String storeSize) {}
    public record SearchResult(long total, long took, List<String> documents) {}
}
