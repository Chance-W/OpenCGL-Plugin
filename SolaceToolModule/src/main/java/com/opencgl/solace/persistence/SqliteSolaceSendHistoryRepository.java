package com.opencgl.solace.persistence;

import com.opencgl.solace.model.SolaceSendHistoryItem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** SQLite persistence used by the sender History tab. */
public final class SqliteSolaceSendHistoryRepository {
    private static final String TABLE = "SOLACE_SEND_HISTORY";
    private final Path database;

    public SqliteSolaceSendHistoryRepository(Path database) throws IOException {
        this.database = database.toAbsolutePath().normalize();
        initialise();
    }

    public synchronized void insert(SolaceSendHistoryItem item) throws IOException {
        String sql = "INSERT OR REPLACE INTO " + TABLE + " (ID, NODE_ID, TIMESTAMP, STATUS, SUMMARY," +
            " DESTINATION_TYPE, DESTINATION, DELIVERY_MODE, CONTENT_TYPE, CORRELATION_ID, REPLY_TO," +
            " TTL_MILLIS, BODY, PROPERTIES_JSON, RESULT, CORRELATION_KEY, DURATION_MILLIS)" +
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, item.getId());
            if (item.getNodeId() == null) statement.setObject(2, null); else statement.setLong(2, item.getNodeId());
            statement.setLong(3, item.getTimestamp() == null ? System.currentTimeMillis() : item.getTimestamp().getTime());
            statement.setString(4, item.getStatus());
            statement.setString(5, item.getSummary());
            statement.setString(6, item.getDestinationType());
            statement.setString(7, item.getDestination());
            statement.setString(8, item.getDeliveryMode());
            statement.setString(9, item.getContentType());
            statement.setString(10, item.getCorrelationId());
            statement.setString(11, item.getReplyTo());
            statement.setLong(12, item.getTtlMillis());
            statement.setString(13, item.getBody());
            statement.setString(14, item.getPropertiesJson());
            statement.setString(15, item.getResult());
            statement.setString(16, item.getCorrelationKey());
            statement.setLong(17, item.getDurationMillis());
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new IOException("保存 Solace 发送历史失败", error);
        }
    }

    public synchronized List<SolaceSendHistoryItem> query() throws IOException {
        List<SolaceSendHistoryItem> result = new ArrayList<>();
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(
            "SELECT * FROM " + TABLE + " ORDER BY TIMESTAMP DESC, ID DESC LIMIT 200");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) result.add(read(rows));
        } catch (SQLException error) {
            throw new IOException("读取 Solace 发送历史失败", error);
        }
        return result;
    }

    public synchronized void delete(String id) throws IOException {
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(
            "DELETE FROM " + TABLE + " WHERE ID = ?")) {
            statement.setString(1, id);
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new IOException("删除 Solace 发送历史失败", error);
        }
    }

    public synchronized void clear() throws IOException {
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(
            "DELETE FROM " + TABLE)) {
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new IOException("清空 Solace 发送历史失败", error);
        }
    }

    private void initialise() throws IOException {
        try {
            Path parent = database.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                    "ID VARCHAR(80) PRIMARY KEY, NODE_ID INTEGER, TIMESTAMP INTEGER NOT NULL," +
                    "STATUS VARCHAR(32), SUMMARY TEXT, DESTINATION_TYPE VARCHAR(16), DESTINATION TEXT," +
                    "DELIVERY_MODE VARCHAR(32), CONTENT_TYPE TEXT, CORRELATION_ID TEXT, REPLY_TO TEXT," +
                    "TTL_MILLIS INTEGER, BODY TEXT, PROPERTIES_JSON TEXT, RESULT TEXT," +
                    "CORRELATION_KEY TEXT, DURATION_MILLIS INTEGER)")) {
                statement.executeUpdate();
            }
        } catch (SQLException error) {
            throw new IOException("创建 Solace 发送历史表失败", error);
        }
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + database);
    }

    private SolaceSendHistoryItem read(ResultSet row) throws SQLException {
        SolaceSendHistoryItem item = new SolaceSendHistoryItem();
        item.setId(row.getString("ID"));
        long nodeId = row.getLong("NODE_ID");
        item.setNodeId(row.wasNull() ? null : nodeId);
        item.setTimestamp(new Date(row.getLong("TIMESTAMP")));
        item.setStatus(row.getString("STATUS"));
        item.setSummary(row.getString("SUMMARY"));
        item.setDestinationType(row.getString("DESTINATION_TYPE"));
        item.setDestination(row.getString("DESTINATION"));
        item.setDeliveryMode(row.getString("DELIVERY_MODE"));
        item.setContentType(row.getString("CONTENT_TYPE"));
        item.setCorrelationId(row.getString("CORRELATION_ID"));
        item.setReplyTo(row.getString("REPLY_TO"));
        item.setTtlMillis(row.getLong("TTL_MILLIS"));
        item.setBody(row.getString("BODY"));
        item.setPropertiesJson(row.getString("PROPERTIES_JSON"));
        item.setResult(row.getString("RESULT"));
        item.setCorrelationKey(row.getString("CORRELATION_KEY"));
        item.setDurationMillis(row.getLong("DURATION_MILLIS"));
        return item;
    }
}
