package com.opencgl.http.repository.impl;

import com.opencgl.base.model.Base;
import com.opencgl.base.utils.SqliteUtil;
import com.opencgl.http.model.HttpHistoryItem;
import com.opencgl.http.repository.HttpHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * History Repository Implementation
 */
public class HttpHistoryRepositoryImpl implements HttpHistoryRepository {
    private static final Logger logger = LoggerFactory.getLogger(HttpHistoryRepositoryImpl.class);

    public HttpHistoryRepositoryImpl() {
        initializeDatabase();
    }

    @Override
    public void initializeDatabase() {
        try {
            // Updated table name to avoid conflict with old schema
            String sql = "CREATE TABLE IF NOT EXISTS http_history_record (" +
                    "id VARCHAR(64) PRIMARY KEY," +
                    "method VARCHAR(20)," +
                    "url TEXT," +
                    "status_code INTEGER," +
                    "duration INTEGER," +
                    "size INTEGER," +
                    "request_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "request_snapshot TEXT," +
                    "response_snapshot TEXT" +
                    ")";
            SqliteUtil.update(sql);
        } catch (Exception e) {
            logger.error("Failed to init history table", e);
        }
    }

    @Override
    public void save(HttpHistoryItem item) {
        String sql = "INSERT INTO http_history_record (id, method, url, status_code, duration, size, request_time, request_snapshot, response_snapshot) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            SqliteUtil.insert(sql,
                    item.getId(),
                    item.getMethod(),
                    item.getUrl(),
                    item.getStatusCode(),
                    item.getDuration(),
                    item.getSize(),
                    // Store an epoch value. SqliteUtil serializes Date as
                    // Date.toString(), which SQLite's JDBC driver cannot
                    // reliably parse again after reopening the plugin.
                    item.getRequestTime() == null ? null : item.getRequestTime().getTime(),
                    item.getRequestSnapshot(),
                    item.getResponseSnapshot()
            );
        } catch (Exception e) {
            logger.error("Failed to save history", e);
        }
    }

    @Override
    public void delete(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        try {
            SqliteUtil.update("DELETE FROM http_history_record WHERE id = ?", id);
        } catch (Exception e) {
            logger.error("Failed to delete history {}", id, e);
        }
    }

    @Override
    public List<HttpHistoryItem> findRecent(int limit) {
        String sql = "SELECT id, method, url, status_code, duration, size, request_time, request_snapshot, response_snapshot " +
                "FROM http_history_record ORDER BY request_time DESC LIMIT ?";
        List<HttpHistoryItem> items = new ArrayList<>();
        String url = "jdbc:sqlite:" + Base.DB_PATH + "data.db";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(mapItem(rs));
                }
            }
            return items;
        } catch (Exception e) {
            logger.error("Failed to load history", e);
            return Collections.emptyList();
        }
    }

    private HttpHistoryItem mapItem(ResultSet rs) throws Exception {
        HttpHistoryItem item = new HttpHistoryItem();
        item.setId(rs.getString("id"));
        item.setMethod(rs.getString("method"));
        item.setUrl(rs.getString("url"));
        int statusCode = rs.getInt("status_code");
        item.setStatusCode(rs.wasNull() ? null : statusCode);
        long duration = rs.getLong("duration");
        item.setDuration(rs.wasNull() ? null : duration);
        long size = rs.getLong("size");
        item.setSize(rs.wasNull() ? null : size);
        item.setRequestTime(readRequestTime(rs));
        // Keep snapshots as raw TEXT so Fastjson does not flatten hook 后的 JSON body
        item.setRequestSnapshot(rs.getString("request_snapshot"));
        item.setResponseSnapshot(rs.getString("response_snapshot"));
        return item;
    }

    private Date readRequestTime(ResultSet rs) throws Exception {
        String raw = rs.getString("request_time");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new Date(Long.parseLong(raw.trim()));
        } catch (NumberFormatException ignored) {
            // Backward compatibility with records written by the old
            // Date.toString() implementation.
        }
        try {
            Timestamp timestamp = rs.getTimestamp("request_time");
            if (timestamp != null) {
                return new Date(timestamp.getTime());
            }
        } catch (Exception ignored) {
            // Fall through to the legacy Java Date format.
        }
        try {
            return new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH).parse(raw);
        } catch (ParseException ignored) {
            logger.warn("Unable to parse HTTP history timestamp: {}", raw);
            return null;
        }
    }

    @Override
    public void clearAll() {
        try {
            SqliteUtil.update("DELETE FROM http_history_record");
        } catch (Exception e) {
            logger.error("Failed to clear history", e);
        }
    }
}
