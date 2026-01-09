package com.opencgl.aiqa.dao;

import com.opencgl.aiqa.model.ModelConfig;
import com.opencgl.base.utils.SqliteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 模型配置 DAO - SQLite 存储
 */
public class ModelConfigDao {
    private static final Logger logger = LoggerFactory.getLogger(ModelConfigDao.class);
    public static final String TABLE_NAME = "AI_MODEL_CONFIG";

    public ModelConfigDao() {
        checkTable();
    }

    private void checkTable() {
        try {
            if (!SqliteUtil.checkTableExist(TABLE_NAME)) {
                SqliteUtil.update("CREATE TABLE " + TABLE_NAME + " (" +
                    "ID VARCHAR(50) PRIMARY KEY," +
                    "NAME VARCHAR(100) NOT NULL," +
                    "BASE_URL VARCHAR(500)," +
                    "API_KEY VARCHAR(500)," +
                    "MODEL VARCHAR(100)," +
                    "TEMPERATURE DOUBLE DEFAULT 0.7," +
                    "SORT_ORDER INTEGER DEFAULT 0" +
                    ")");
                logger.info("创建表 {} 成功", TABLE_NAME);
                
                // 插入默认配置
                insertData(ModelConfig.createDefault("默认模型"));
            }
        } catch (Exception e) {
            logger.error("检查/创建表失败", e);
        }
    }

    public List<ModelConfig> queryAll() {
        try {
            return SqliteUtil.queryForList(
                "SELECT ID, NAME, BASE_URL AS baseUrl, API_KEY AS apiKey, MODEL, TEMPERATURE, SORT_ORDER FROM " + TABLE_NAME + " ORDER BY SORT_ORDER, NAME",
                ModelConfig.class
            );
        } catch (Exception e) {
            logger.error("查询模型配置失败", e);
            return new ArrayList<>();
        }
    }

    public void insertData(ModelConfig config) {
        try {
            if (config.getId() == null || config.getId().isEmpty()) {
                config.setId(UUID.randomUUID().toString());
            }
            SqliteUtil.update(
                "INSERT INTO " + TABLE_NAME + " (ID, NAME, BASE_URL, API_KEY, MODEL, TEMPERATURE) VALUES (?, ?, ?, ?, ?, ?)",
                config.getId(),
                config.getName(),
                config.getBaseUrl(),
                config.getApiKey(),
                config.getModel(),
                config.getTemperature()
            );
            logger.info("插入模型配置: {}", config.getName());
        } catch (Exception e) {
            logger.error("插入模型配置失败", e);
        }
    }

    public void updateData(ModelConfig config) {
        try {
            SqliteUtil.update(
                "UPDATE " + TABLE_NAME + " SET NAME=?, BASE_URL=?, API_KEY=?, MODEL=?, TEMPERATURE=? WHERE ID=?",
                config.getName(),
                config.getBaseUrl(),
                config.getApiKey(),
                config.getModel(),
                config.getTemperature(),
                config.getId()
            );
            logger.info("更新模型配置: {}", config.getName());
        } catch (Exception e) {
            logger.error("更新模型配置失败", e);
        }
    }

    public void deleteData(String id) {
        try {
            SqliteUtil.update("DELETE FROM " + TABLE_NAME + " WHERE ID=?", id);
            logger.info("删除模型配置: {}", id);
        } catch (Exception e) {
            logger.error("删除模型配置失败", e);
        }
    }
}
