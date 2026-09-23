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
            throw new IllegalStateException("Failed to list HTTP environments", e);
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
            throw new IllegalStateException("Failed to read HTTP environment", e);
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
    public void createEnvironment(String envName, Map<String, String> variables) {
        try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
            conn.setAutoCommit(false);
            try {
                try (var ps = conn.prepareStatement("INSERT INTO http_environment(env_name) VALUES(?)")) {
                    ps.setString(1, envName);
                    ps.executeUpdate();
                }
                try (var ps = conn.prepareStatement("INSERT INTO http_environment_var(env_name,var_key,var_value) VALUES(?,?,?)")) {
                    for (var entry : variables.entrySet()) {
                        ps.setString(1, envName);
                        ps.setString(2, entry.getKey());
                        ps.setString(3, entry.getValue());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                conn.commit();
            } catch (Exception failure) {
                conn.rollback();
                throw failure;
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Failed to create HTTP environment", failure);
        }
    }

    @Override
    public void deleteEnvironment(String envName) {
        if (envName == null || envName.trim().isEmpty()) return;
        try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
            conn.setAutoCommit(false);
            try {
                // Environment service may start before the request-table migration.
                boolean hasAssociation = false;
                try (var ps = conn.prepareStatement("PRAGMA table_info(http_tree_item)"); var rs = ps.executeQuery()) {
                    while (rs.next()) hasAssociation |= "environment_name".equals(rs.getString("name"));
                }
                if (hasAssociation) {
                    try (var ps = conn.prepareStatement("UPDATE http_tree_item SET environment_name=NULL WHERE environment_name=?")) {
                        ps.setString(1, envName); ps.executeUpdate();
                    }
                }
                for (String table : List.of("http_environment_var", "http_environment")) {
                    try (var ps = conn.prepareStatement("DELETE FROM " + table + " WHERE env_name=?")) {
                        ps.setString(1, envName); ps.executeUpdate();
                    }
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            logger.error("Error deleting env: " + envName, e);
            throw new IllegalStateException("Failed to delete HTTP environment", e);
        }
    }
}
