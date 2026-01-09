package com.opencgl.http.repository.impl;

import com.opencgl.base.model.Base;
import com.opencgl.base.utils.SqliteUtil;
import com.opencgl.http.model.HttpTreeItem;
import com.opencgl.http.repository.HttpTreeItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * SQL树节点Repository实现（基于JDBC）
 * 
 * @author Chance.W
 */
public class HttpTreeItemRepositoryImpl implements HttpTreeItemRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(HttpTreeItemRepositoryImpl.class);
    
    public HttpTreeItemRepositoryImpl() {
        initializeDatabase();
    }
    
    @Override
    public void initializeDatabase() {
        try {
            // Check if table exists manually or just try to create it
            // Updated schema with new fields: description, auth_config, hook_script
            // Removed: host, port, database_name, etc. specific to SQL client that were accidentally copied initially?
            // Actually, based on previous schema manually created, it had many SQL Client fields.
            // We should use a cleaner schema for HTTP Debugger.
            
            String sql = "CREATE TABLE IF NOT EXISTS http_tree_item (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "parent_id INTEGER," +
                    "name VARCHAR(200) NOT NULL," +
                    "is_leaf BOOLEAN DEFAULT 0," +
                    "sort_order INTEGER DEFAULT 0," +
                    "node_type VARCHAR(50) NOT NULL," +
                    "icon_name VARCHAR(50)," +
                    "description TEXT," +
                    "method VARCHAR(20)," +
                    "url TEXT," +
                    "headers TEXT," +
                    "params TEXT," +
                    "body TEXT," +
                    "body_type VARCHAR(20)," +
                    "auth_config TEXT," +
                    "hook_script VARCHAR(200)," +
                    "timeout INTEGER," +
                    "ssl_verification BOOLEAN," +
                    "follow_redirects BOOLEAN," +
                    "client_cert_path VARCHAR(500)," +
                    "client_cert_pass VARCHAR(200)," +
                    "server_cert_path VARCHAR(500)," +
                    "server_cert_pass VARCHAR(200)," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")";
            
            SqliteUtil.update(sql);
            logger.info("Database initialized successfully");

            // Migration: add columns only if they do not exist (idempotent for duplicate column)
            Set<String> existing = getExistingColumns("http_tree_item");
            addColumnIfNotExists(existing, "ssl_verification", "ALTER TABLE http_tree_item ADD COLUMN ssl_verification BOOLEAN");
            addColumnIfNotExists(existing, "follow_redirects", "ALTER TABLE http_tree_item ADD COLUMN follow_redirects BOOLEAN");
            addColumnIfNotExists(existing, "client_cert_path", "ALTER TABLE http_tree_item ADD COLUMN client_cert_path VARCHAR(500)");
            addColumnIfNotExists(existing, "client_cert_pass", "ALTER TABLE http_tree_item ADD COLUMN client_cert_pass VARCHAR(200)");
            addColumnIfNotExists(existing, "server_cert_path", "ALTER TABLE http_tree_item ADD COLUMN server_cert_path VARCHAR(500)");
            addColumnIfNotExists(existing, "server_cert_pass", "ALTER TABLE http_tree_item ADD COLUMN server_cert_pass VARCHAR(200)");

        } catch (Exception e) {
            logger.error("Failed to initialize database", e);
        }
    }

    private static Set<String> getExistingColumns(String tableName) {
        Set<String> columns = new HashSet<>();
        String url = "jdbc:sqlite:" + Base.DB_PATH + "data.db";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement("PRAGMA table_info(" + tableName + ")");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString("name");
                if (name != null) columns.add(name);
            }
        } catch (Exception e) {
            logger.debug("Could not read table info for {}", tableName, e);
        }
        return columns;
    }

    private static void addColumnIfNotExists(Set<String> existing, String columnName, String alterSql) {
        if (existing.contains(columnName)) return;
        try {
            SqliteUtil.update(alterSql);
            existing.add(columnName);
        } catch (Exception e) {
            logger.warn("Migration add column {} failed (may already exist): {}", columnName, e.getMessage());
        }
    }
    
    @Override
    public List<HttpTreeItem> findAll() {
        String sql = "SELECT * FROM http_tree_item ORDER BY sort_order";
        try {
            return SqliteUtil.queryForList(sql, HttpTreeItem.class);
        } catch (Exception e) {
            logger.error("Error finding all items", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public Optional<HttpTreeItem> findById(Long id) {
        String sql = "SELECT * FROM http_tree_item WHERE id = ?";
        try {
            List<HttpTreeItem> list = SqliteUtil.queryForList(sql, HttpTreeItem.class, id);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        } catch (Exception e) {
            logger.error("Error finding item by id: " + id, e);
            return Optional.empty();
        }
    }
    
    @Override
    public List<HttpTreeItem> findByParentId(Long parentId) {
        String sql = "SELECT * FROM http_tree_item WHERE parent_id = ? ORDER BY sort_order";
        try {
            if (parentId == null) {
                return SqliteUtil.queryForList(sql, HttpTreeItem.class, 0L);
            }
            return SqliteUtil.queryForList(sql, HttpTreeItem.class, parentId);
        } catch (Exception e) {
            logger.error("Error finding items by parent_id: " + parentId, e);
            return new ArrayList<>();
        }
    }
    
    public List<HttpTreeItem> findByNodeType(String nodeType) {
        String sql = "SELECT * FROM http_tree_item WHERE node_type = ? ORDER BY sort_order";
        try {
            return SqliteUtil.queryForList(sql, HttpTreeItem.class, nodeType);
        } catch (Exception e) {
            logger.error("Error finding items by node_type: " + nodeType, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public HttpTreeItem save(HttpTreeItem item) {
        if (item.getId() == null) {
            return insert(item);
        } else {
            return update(item);
        }
    }
    
    private HttpTreeItem insert(HttpTreeItem item) {
        String sql = "INSERT INTO http_tree_item (parent_id, name, is_leaf, sort_order, node_type, icon_name, description, " +
                "method, url, headers, params, body, body_type, auth_config, hook_script, timeout, ssl_verification, follow_redirects, " +
                "client_cert_path, client_cert_pass, server_cert_path, server_cert_pass) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try {
            long id = SqliteUtil.insert(sql, 
                item.getParentId(), item.getName(), item.getIsLeaf(), item.getSortOrder(), item.getNodeType(), item.getIconName(), item.getDescription(),
                item.getMethod(), item.getUrl(), item.getHeaders(), item.getParams(), item.getBody(), item.getBodyType(),
                item.getAuthConfig(), item.getHookScript(), item.getTimeout(), item.getSslVerification(), item.getFollowRedirects(),
                item.getClientCertPath(), item.getClientCertPass(), item.getServerCertPath(), item.getServerCertPass()
            );
            item.setId(id);
        } catch (Exception e) {
            logger.error("Error inserting item", e);
        }
        return item;
    }
    
    private HttpTreeItem update(HttpTreeItem item) {
        String sql = "UPDATE http_tree_item SET parent_id=?, name=?, is_leaf=?, sort_order=?, node_type=?, icon_name=?, description=?, " +
                "method=?, url=?, headers=?, params=?, body=?, body_type=?, auth_config=?, hook_script=?, timeout=?, ssl_verification=?, follow_redirects=?, " +
                "client_cert_path=?, client_cert_pass=?, server_cert_path=?, server_cert_pass=?, " +
                "updated_at=CURRENT_TIMESTAMP WHERE id=?";
        
        try {
            SqliteUtil.update(sql,
                item.getParentId(), item.getName(), item.getIsLeaf(), item.getSortOrder(), item.getNodeType(), item.getIconName(), item.getDescription(),
                item.getMethod(), item.getUrl(), item.getHeaders(), item.getParams(), item.getBody(), item.getBodyType(),
                item.getAuthConfig(), item.getHookScript(), item.getTimeout(), item.getSslVerification(), item.getFollowRedirects(),
                item.getClientCertPath(), item.getClientCertPass(), item.getServerCertPath(), item.getServerCertPass(),
                item.getId()
            );
        } catch (Exception e) {
            logger.error("Error updating item", e);
        }
        return item;
    }
    
    public void saveAll(List<HttpTreeItem> items) {
        for (HttpTreeItem item : items) {
            save(item);
        }
    }
    
    @Override
    public void delete(HttpTreeItem item) {
        deleteById(item.getId());
    }
    
    @Override
    public void deleteById(Long id) {
        String sql = "DELETE FROM http_tree_item WHERE id = ?";
        try {
            SqliteUtil.update(sql, id);
        } catch (Exception e) {
            logger.error("Error deleting item: " + id, e);
        }
    }
    
    @Override
    public void deleteWithChildren(Long id) {
        // Recursive delete
        List<HttpTreeItem> children = findByParentId(id);
        for (HttpTreeItem child : children) {
            deleteWithChildren(child.getId());
        }
        deleteById(id);
    }
    
    @Override
    public void updateSortOrder(Long id, Integer sortOrder) {
        String sql = "UPDATE http_tree_item SET sort_order = ? WHERE id = ?";
        try {
            SqliteUtil.update(sql, sortOrder, id);
        } catch (Exception e) {
            logger.error("Error updating sort order", e);
        }
    }
}
