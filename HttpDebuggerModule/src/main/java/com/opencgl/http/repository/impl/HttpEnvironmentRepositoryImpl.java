package com.opencgl.http.repository.impl;

import com.opencgl.base.model.Base;
import com.opencgl.base.utils.SqliteUtil;
import com.opencgl.http.repository.HttpEnvironmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 环境与变量 SQLite 持久化实现（与 http_tree_item 共用 data.db）
 * "None" 仅表示未配置，不入库、不展示在环境列表中。
 */
public class HttpEnvironmentRepositoryImpl implements HttpEnvironmentRepository {

    /** 与 EnvironmentService.DEFAULT_ENV_NAME 一致，仅占位不入库 */
    private static final String DEFAULT_ENV_NAME = "None";

    private static final Logger logger = LoggerFactory.getLogger(HttpEnvironmentRepositoryImpl.class);
    private static final String JDBC_URL = "jdbc:sqlite:" + Base.DB_PATH + "data.db";

    public HttpEnvironmentRepositoryImpl() {
        initializeDatabase();
    }

    @Override
    public void initializeDatabase() {
        try {
            String envTable = "CREATE TABLE IF NOT EXISTS http_environment (" +
                    "env_name VARCHAR(200) PRIMARY KEY" +
                    ")";
            String varTable = "CREATE TABLE IF NOT EXISTS http_environment_var (" +
                    "env_name VARCHAR(200) NOT NULL," +
                    "var_key VARCHAR(500) NOT NULL," +
                    "var_value TEXT," +
                    "PRIMARY KEY (env_name, var_key)" +
                    ")";
            SqliteUtil.update(envTable);
            SqliteUtil.update(varTable);
            // 若表里曾有旧版插入的 "None"，删掉，避免出现在环境列表中
            try {
                SqliteUtil.update("DELETE FROM http_environment_var WHERE env_name = ?", DEFAULT_ENV_NAME);
                SqliteUtil.update("DELETE FROM http_environment WHERE env_name = ?", DEFAULT_ENV_NAME);
            } catch (Exception ignored) { }
            logger.info("HTTP environment tables initialized");
        } catch (Exception e) {
            logger.error("Failed to initialize environment database", e);
        }
    }

    @Override
    public void ensureDefaultEnv() {
        // "None" 仅表示未配置，不写入数据库，环境列表中不展示
    }

    @Override
    public List<String> findAllEnvNames() {
        List<String> names = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(JDBC_URL);
             PreparedStatement ps = conn.prepareStatement("SELECT env_name FROM http_environment ORDER BY env_name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String n = rs.getString("env_name");
                if (n != null && !DEFAULT_ENV_NAME.equals(n)) names.add(n);
            }
        } catch (Exception e) {
            logger.error("Error listing env names", e);
        }
        return names;
    }

    @Override
    public Map<String, String> getVariables(String envName) {
        Map<String, String> out = new HashMap<>();
        try (Connection conn = DriverManager.getConnection(JDBC_URL);
             PreparedStatement ps = conn.prepareStatement("SELECT var_key, var_value FROM http_environment_var WHERE env_name = ?")) {
            ps.setString(1, envName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String k = rs.getString("var_key");
                    String v = rs.getString("var_value");
                    if (k != null) out.put(k, v != null ? v : "");
                }
            }
        } catch (Exception e) {
            logger.error("Error loading vars for env: " + envName, e);
        }
        return out;
    }

    @Override
    public void saveEnvironment(String envName, Map<String, String> variables) {
        try {
            SqliteUtil.update("INSERT OR IGNORE INTO http_environment (env_name) VALUES (?)", envName);
            SqliteUtil.update("DELETE FROM http_environment_var WHERE env_name = ?", envName);
            if (variables != null) {
                for (Map.Entry<String, String> e : variables.entrySet()) {
                    SqliteUtil.update(
                            "INSERT INTO http_environment_var (env_name, var_key, var_value) VALUES (?, ?, ?)",
                            envName, e.getKey(), e.getValue() != null ? e.getValue() : "");
                }
            }
        } catch (Exception e) {
            logger.error("Error saving env: " + envName, e);
        }
    }

    @Override
    public void deleteEnvironment(String envName) {
        if (envName == null || envName.trim().isEmpty()) return;
        try {
            SqliteUtil.update("DELETE FROM http_environment_var WHERE env_name = ?", envName);
            SqliteUtil.update("DELETE FROM http_environment WHERE env_name = ?", envName);
        } catch (Exception e) {
            logger.error("Error deleting env: " + envName, e);
        }
    }
}
