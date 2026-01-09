package com.opencgl.http.repository.impl;

import com.opencgl.base.utils.SqliteUtil;
import com.opencgl.http.model.HttpHistoryItem;
import com.opencgl.http.repository.HttpHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

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
                    item.getRequestTime(),
                    item.getRequestSnapshot(),
                    item.getResponseSnapshot()
            );
        } catch (Exception e) {
            logger.error("Failed to save history", e);
        }
    }

    @Override
    public List<HttpHistoryItem> findRecent(int limit) {
        String sql = "SELECT * FROM http_history_record ORDER BY request_time DESC LIMIT ?";
        try {
            return SqliteUtil.queryForList(sql, HttpHistoryItem.class, limit);
        } catch (Exception e) {
            logger.error("Failed to load history", e);
            return Collections.emptyList();
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
