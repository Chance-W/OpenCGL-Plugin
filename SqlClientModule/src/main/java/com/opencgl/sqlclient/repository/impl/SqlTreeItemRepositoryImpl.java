package com.opencgl.sqlclient.repository.impl;

import com.opencgl.base.utils.SqliteUtil;
import com.opencgl.sqlclient.model.SqlTreeItem;
import com.opencgl.sqlclient.repository.SqlTreeItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQL树节点Repository实现
 * 使用公共SqliteUtil进行操作
 *
 * @author Chance.W
 */
public class SqlTreeItemRepositoryImpl implements SqlTreeItemRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(SqlTreeItemRepositoryImpl.class);
    
    public SqlTreeItemRepositoryImpl() {
        initializeDatabase();
    }

    @Override
    public void initializeDatabase() {
        try {
            if (!SqliteUtil.checkTableExist("sql_tree_item")) {
                logger.warn("Table sql_tree_item not found, creating...");
                createTable();
            }
        } catch (Exception e) {
            logger.error("Failed to initialize database", e);
        }
    }
    
    private void createTable() throws Exception {
        String sql = "CREATE TABLE IF NOT EXISTS sql_tree_item (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "parent_id INTEGER," +
                "name VARCHAR(200) NOT NULL," +
                "is_leaf BOOLEAN DEFAULT 0," +
                "sort_order INTEGER DEFAULT 0," +
                "node_type VARCHAR(50) NOT NULL," +
                "icon_name VARCHAR(50)," +
                "host VARCHAR(200)," +
                "port INTEGER," +
                "database_name VARCHAR(100)," +
                "username VARCHAR(100)," +
                "password VARCHAR(500)," +
                "db_type VARCHAR(20)," +
                "schema_name VARCHAR(100)," +
                "table_name VARCHAR(100)," +
                "object_type VARCHAR(50)," +
                "object_ddl TEXT," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";
        
        SqliteUtil.update(sql);
    }
    
    @Override
    public List<SqlTreeItem> findAll() {
        String sql = "SELECT * FROM sql_tree_item ORDER BY sort_order";
        try {
            return SqliteUtil.queryForList(sql, SqlTreeItem.class);
        } catch (Exception e) {
            logger.error("Error finding all items", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public Optional<SqlTreeItem> findById(Long id) {
        String sql = "SELECT * FROM sql_tree_item WHERE id = ?";
        try {
            List<SqlTreeItem> list = SqliteUtil.queryForList(sql, SqlTreeItem.class, id);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        } catch (Exception e) {
            logger.error("Error finding item by id: " + id, e);
            return Optional.empty();
        }
    }
    
    @Override
    public List<SqlTreeItem> findByParentId(Long parentId) {
        String sql = "SELECT * FROM sql_tree_item WHERE parent_id = ? ORDER BY sort_order";
        
        try {
            if (parentId == null) {
                sql = "SELECT * FROM sql_tree_item WHERE parent_id IS NULL ORDER BY sort_order";
                return SqliteUtil.queryForList(sql, SqlTreeItem.class);
            } else {
                return SqliteUtil.queryForList(sql, SqlTreeItem.class, parentId);
            }
        } catch (Exception e) {
            logger.error("Error finding items by parent_id: " + parentId, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<SqlTreeItem> findByNodeType(String nodeType) {
        String sql = "SELECT * FROM sql_tree_item WHERE node_type = ? ORDER BY sort_order";
        try {
            return SqliteUtil.queryForList(sql, SqlTreeItem.class, nodeType);
        } catch (Exception e) {
            logger.error("Error finding items by node_type: " + nodeType, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public SqlTreeItem save(SqlTreeItem item) {
        if (item.getId() == null) {
            return insert(item);
        } else {
            return update(item);
        }
    }
    
    private SqlTreeItem insert(SqlTreeItem item) {
        String sql = "INSERT INTO sql_tree_item (parent_id, name, is_leaf, sort_order, node_type, icon_name, " +
                "host, port, database_name, username, password, db_type, schema_name, table_name, object_type, object_ddl) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        Object[] params = new Object[] {
            item.getParentId(), item.getName(), item.getIsLeaf(), item.getSortOrder(),
            item.getNodeType(),
            item.getIconName(), item.getHost(), item.getPort(), item.getDatabaseName(),
            item.getUsername(), item.getPassword(), item.getDbType(),
            item.getSchemaName(), item.getTableName(), item.getObjectType(), item.getObjectDdl()
        };
        
        try {
            Long id = SqliteUtil.insert(sql, params);
            if (id != null) {
                item.setId(id);
            }
        } catch (Exception e) {
            logger.error("Error inserting item", e);
        }
        
        return item;
    }
    
    private SqlTreeItem update(SqlTreeItem item) {
        String sql = "UPDATE sql_tree_item SET parent_id=?, name=?, is_leaf=?, sort_order=?, node_type=?, icon_name=?, " +
                "host=?, port=?, database_name=?, username=?, password=?, db_type=?, schema_name=?, table_name=?, " +
                "object_type=?, object_ddl=?, updated_at=CURRENT_TIMESTAMP WHERE id=?";
        
        Object[] params = new Object[] {
            item.getParentId(), item.getName(), item.getIsLeaf(), item.getSortOrder(),
            item.getNodeType(),
            item.getIconName(), item.getHost(), item.getPort(), item.getDatabaseName(),
            item.getUsername(), item.getPassword(), item.getDbType(),
            item.getSchemaName(), item.getTableName(), item.getObjectType(), item.getObjectDdl(),
            item.getId()
        };
        
        try {
            SqliteUtil.update(sql, params);
        } catch (Exception e) {
            logger.error("Error updating item", e);
        }
        
        return item;
    }
    
    @Override
    public void saveAll(List<SqlTreeItem> items) {
        // SqliteUtil目前不支持批量，只能循环
        for (SqlTreeItem item : items) {
            save(item);
        }
    }
    
    @Override
    public void delete(SqlTreeItem item) {
        deleteById(item.getId());
    }
    
    @Override
    public void deleteById(Long id) {
        String sql = "DELETE FROM sql_tree_item WHERE id = ?";
        try {
            SqliteUtil.update(sql, id);
        } catch (Exception e) {
            logger.error("Error deleting item: " + id, e);
        }
    }
    
    @Override
    public void deleteWithChildren(Long id) {
        // 递归删除，先查子节点
        List<SqlTreeItem> children = findByParentId(id);
        for (SqlTreeItem child : children) {
            deleteWithChildren(child.getId());
        }
        deleteById(id);
    }
    
    @Override
    public void updateSortOrder(Long id, Integer sortOrder) {
        String sql = "UPDATE sql_tree_item SET sort_order = ? WHERE id = ?";
        try {
            SqliteUtil.update(sql, sortOrder, id);
        } catch (Exception e) {
            logger.error("Error updating sort order", e);
        }
    }
}
