package com.opencgl.mongodb.service;

import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * MongoDB 服务
 * 支持连接、CRUD、索引管理、聚合查询
 *
 * @author OpenCGL
 */
public class MongoDBService implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(MongoDBService.class);

    private MongoClient mongoClient;
    private String currentDatabase;

    /**
     * 连接到 MongoDB
     */
    public void connect(String connectionString) {
        close();
        mongoClient = MongoClients.create(connectionString);
    }

    /**
     * 测试连接
     */
    public boolean testConnection() {
        if (mongoClient == null) return false;
        try {
            mongoClient.listDatabaseNames().first();
            return true;
        } catch (Exception e) {
            logger.error("MongoDB 连接测试失败", e);
            return false;
        }
    }

    /**
     * 获取数据库列表
     */
    public List<String> listDatabases() {
        if (mongoClient == null) throw new IllegalStateException("未连接");
        List<String> databases = new ArrayList<>();
        for (String name : mongoClient.listDatabaseNames()) {
            databases.add(name);
        }
        return databases;
    }

    /**
     * 设置当前数据库
     */
    public void useDatabase(String database) {
        this.currentDatabase = database;
    }

    /**
     * 获取集合列表
     */
    public List<String> listCollections(String database) {
        if (mongoClient == null) throw new IllegalStateException("未连接");
        MongoDatabase db = mongoClient.getDatabase(database);
        List<String> collections = new ArrayList<>();
        for (String name : db.listCollectionNames()) {
            collections.add(name);
        }
        return collections;
    }

    /**
     * 创建集合
     */
    public void createCollection(String database, String collectionName) {
        MongoDatabase db = mongoClient.getDatabase(database);
        db.createCollection(collectionName);
    }

    /**
     * 删除集合
     */
    public void dropCollection(String database, String collectionName) {
        MongoDatabase db = mongoClient.getDatabase(database);
        db.getCollection(collectionName).drop();
    }

    // ==================== 文档操作 ====================

    /**
     * 查询文档
     */
    public List<Document> find(String database, String collection, String filterJson, int limit) {
        MongoDatabase db = mongoClient.getDatabase(database);
        MongoCollection<Document> coll = db.getCollection(collection);
        
        Document filter = filterJson == null || filterJson.isEmpty() 
            ? new Document() 
            : Document.parse(filterJson);
        
        List<Document> results = new ArrayList<>();
        FindIterable<Document> iterable = coll.find(filter).limit(limit > 0 ? limit : 100);
        for (Document doc : iterable) {
            results.add(doc);
        }
        return results;
    }

    /**
     * 插入文档
     */
    public String insertOne(String database, String collection, String documentJson) {
        MongoDatabase db = mongoClient.getDatabase(database);
        MongoCollection<Document> coll = db.getCollection(collection);
        
        Document doc = Document.parse(documentJson);
        InsertOneResult result = coll.insertOne(doc);
        
        return result.getInsertedId() != null 
            ? result.getInsertedId().asObjectId().getValue().toHexString() 
            : null;
    }

    /**
     * 更新文档
     */
    public long updateOne(String database, String collection, String filterJson, String updateJson) {
        MongoDatabase db = mongoClient.getDatabase(database);
        MongoCollection<Document> coll = db.getCollection(collection);
        
        Document filter = Document.parse(filterJson);
        Document update = Document.parse(updateJson);
        
        // 确保 update 包含 $set 操作符
        if (!update.containsKey("$set") && !update.containsKey("$unset") && !update.containsKey("$inc")) {
            update = new Document("$set", update);
        }
        
        UpdateResult result = coll.updateOne(filter, update);
        return result.getModifiedCount();
    }

    /**
     * 删除文档
     */
    public long deleteOne(String database, String collection, String filterJson) {
        MongoDatabase db = mongoClient.getDatabase(database);
        MongoCollection<Document> coll = db.getCollection(collection);
        
        Document filter = Document.parse(filterJson);
        DeleteResult result = coll.deleteOne(filter);
        return result.getDeletedCount();
    }

    /**
     * 删除多个文档
     */
    public long deleteMany(String database, String collection, String filterJson) {
        MongoDatabase db = mongoClient.getDatabase(database);
        MongoCollection<Document> coll = db.getCollection(collection);
        
        Document filter = Document.parse(filterJson);
        DeleteResult result = coll.deleteMany(filter);
        return result.getDeletedCount();
    }

    /**
     * 统计文档数量
     */
    public long countDocuments(String database, String collection, String filterJson) {
        MongoDatabase db = mongoClient.getDatabase(database);
        MongoCollection<Document> coll = db.getCollection(collection);
        
        Document filter = filterJson == null || filterJson.isEmpty() 
            ? new Document() 
            : Document.parse(filterJson);
        
        return coll.countDocuments(filter);
    }

    // ==================== 索引管理 ====================

    /**
     * 获取索引列表
     */
    public List<Document> listIndexes(String database, String collection) {
        MongoDatabase db = mongoClient.getDatabase(database);
        MongoCollection<Document> coll = db.getCollection(collection);
        
        List<Document> indexes = new ArrayList<>();
        for (Document index : coll.listIndexes()) {
            indexes.add(index);
        }
        return indexes;
    }

    /**
     * 创建索引
     */
    public String createIndex(String database, String collection, String keysJson, boolean unique) {
        MongoDatabase db = mongoClient.getDatabase(database);
        MongoCollection<Document> coll = db.getCollection(collection);
        
        Document keys = Document.parse(keysJson);
        if (unique) {
            return coll.createIndex(keys, new IndexOptions().unique(true));
        }
        return coll.createIndex(keys);
    }

    /**
     * 删除索引
     */
    public void dropIndex(String database, String collection, String indexName) {
        MongoDatabase db = mongoClient.getDatabase(database);
        MongoCollection<Document> coll = db.getCollection(collection);
        coll.dropIndex(indexName);
    }

    // ==================== 聚合查询 ====================

    /**
     * 聚合查询
     */
    public List<Document> aggregate(String database, String collection, String pipelineJson) {
        MongoDatabase db = mongoClient.getDatabase(database);
        MongoCollection<Document> coll = db.getCollection(collection);
        
        // 解析聚合管道 JSON 数组
        List<Document> pipeline = new ArrayList<>();
        Document pipelineDoc = Document.parse("{\"pipeline\":" + pipelineJson + "}");
        List<Document> stages = pipelineDoc.getList("pipeline", Document.class);
        pipeline.addAll(stages);
        
        List<Document> results = new ArrayList<>();
        for (Document doc : coll.aggregate(pipeline)) {
            results.add(doc);
        }
        return results;
    }

    @Override
    public void close() {
        MongoClient clientToClose = mongoClient;
        mongoClient = null;
        if (clientToClose != null) {
            try {
                clientToClose.close();
            } catch (RuntimeException e) {
                logger.error("关闭 MongoDB 连接失败", e);
            }
        }
    }
}
