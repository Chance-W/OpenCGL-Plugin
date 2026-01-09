package com.opencgl.sqlclient.service;

import com.opencgl.sqlclient.model.DbConnection;
import com.opencgl.sqlclient.util.SqlSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseService {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);
    
    private final Map<String, Connection> connectionPool = new ConcurrentHashMap<>();
    
    public Connection connect(DbConnection dbConn) throws SQLException {
        String url = dbConn.buildJdbcUrl();
        loadDriver(dbConn);
        
        Connection conn;
        if (dbConn.getType() == DbConnection.DbType.SQLITE) {
            conn = DriverManager.getConnection(url);
        } else {
            conn = DriverManager.getConnection(url, dbConn.getUsername(), dbConn.getPassword());
        }
        
        Connection previous = connectionPool.put(dbConn.getName(), conn);
        if (previous != null && previous != conn) {
            try {
                previous.close();
            } catch (SQLException e) {
                logger.warn("Failed to close previous connection: {}", dbConn.getName(), e);
            }
        }
        logger.info("Connected to: {}", dbConn.getName());
        return conn;
    }
    
    public void disconnect(String connName) {
        try {
            Connection conn = connectionPool.remove(connName);
            if (conn != null && !conn.isClosed()) {
                conn.close();
                logger.info("Disconnected: {}", connName);
            }
        } catch (SQLException e) {
            logger.error("Failed to disconnect", e);
        }
    }
    
    public void testConnection(DbConnection dbConn) throws SQLException {
        String url = dbConn.buildJdbcUrl();
        Connection conn = null;
        try {
            loadDriver(dbConn);
            if (dbConn.getType() == DbConnection.DbType.SQLITE) {
                conn = DriverManager.getConnection(url);
            } else {
                conn = DriverManager.getConnection(url, dbConn.getUsername(), dbConn.getPassword());
            }
            logger.info("Test connection successful: {}", dbConn.getName());
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    // Ignore
                }
            }
        }
    }

    /** Fat-JAR 中多个 JDBC 驱动的 service 文件可能被覆盖，显式加载确保 DriverManager 可发现驱动。 */
    private void loadDriver(DbConnection dbConn) throws SQLException {
        String driver = switch (dbConn.getType()) {
            case SQLITE -> "org.sqlite.JDBC";
            case MYSQL -> "com.mysql.cj.jdbc.Driver";
            case MARIADB -> "org.mariadb.jdbc.Driver";
            case POSTGRESQL -> "org.postgresql.Driver";
            case ORACLE -> "oracle.jdbc.OracleDriver";
            case SQL_SERVER -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            case H2 -> "org.h2.Driver";
        };
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            throw new SQLException("未找到 " + dbConn.getType() + " JDBC 驱动，请重新安装最新 SQLClient 插件", e);
        }
    }
    
    public Connection getConnection(String connName) {
        return connectionPool.get(connName);
    }
    
    public List<String> getTables(Connection conn) throws SQLException {
        List<String> tables = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        return tables;
    }
    
    public List<String> getColumns(Connection conn, String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, tableName, "%")) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                String columnType = rs.getString("TYPE_NAME");
                columns.add(columnName + " (" + columnType + ")");
            }
        }
        return columns;
    }
    
    public QueryResult executeQuery(Connection conn, String sql) throws SQLException {
        sql = SqlSanitizer.prepare(sql);
        if (sql.isBlank()) throw new SQLException("SQL 仅包含注释或分隔符");
        try (Statement stmt = conn.createStatement()) {
            // 检查是否是SELECT语句
            String trimmedSql = sql.trim().toUpperCase();
            if (trimmedSql.startsWith("SELECT") || trimmedSql.startsWith("SHOW") ||
                trimmedSql.startsWith("DESC") || trimmedSql.startsWith("EXPLAIN")) {
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    return buildQueryResult(rs);
                }
            } else {
                int rowsAffected = stmt.executeUpdate(sql);
                QueryResult result = new QueryResult();
                result.message = "执行成功，影响行数: " + rowsAffected;
                return result;
            }
        }
    }
    
    private QueryResult buildQueryResult(ResultSet rs) throws SQLException {
        QueryResult result = new QueryResult();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        
        // 列名
        for (int i = 1; i <= columnCount; i++) {
            result.columns.add(metaData.getColumnName(i));
        }
        
        // 数据行
        while (rs.next()) {
            List<Object> row = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                row.add(rs.getObject(i));
            }
            result.rows.add(row);
        }
        
        result.message = "查询成功，返回 " + result.rows.size() + " 行";
        return result;
    }
    
    public void disconnectAll() {
        List<Connection> connections = new ArrayList<>(connectionPool.values());
        connectionPool.clear();
        for (Connection connection : connections) {
            if (connection == null) {
                continue;
            }
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                logger.error("Failed to disconnect SQL connection", e);
            }
        }
    }
    
    public static class QueryResult {
        public List<String> columns = new ArrayList<>();
        public List<List<Object>> rows = new ArrayList<>();
        public String message = "";
    }
}
