package com.opencgl.http.repository;

import com.opencgl.base.model.Base;
import com.opencgl.http.repository.impl.HttpTreeItemRepositoryImpl;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

class HttpEnvironmentMigrationTest {
    @Test void startupAddsMissingColumnWithoutChangingLegacyDataAndIsRepeatable() throws Exception {
        assertTrue(System.getProperty("user.home").endsWith("test-home"));
        Files.createDirectories(Path.of(Base.DB_PATH));
        new HttpTreeItemRepositoryImpl();
        try (var conn = DriverManager.getConnection("jdbc:sqlite:" + Base.DB_PATH + "data.db");
             var sql = conn.createStatement()) {
            boolean exists = false;
            try (var columns = sql.executeQuery("PRAGMA table_info(http_tree_item)")) {
                while (columns.next()) exists |= "environment_name".equals(columns.getString("name"));
            }
            if (exists) sql.executeUpdate("ALTER TABLE http_tree_item DROP COLUMN environment_name");
            sql.executeUpdate("INSERT INTO http_tree_item(id,name,node_type,is_leaf,body) VALUES(-789,'legacy','REQUEST',1,'旧内容')");
            try {
                new HttpTreeItemRepositoryImpl();
                boolean migrated = false;
                try (var columns = sql.executeQuery("PRAGMA table_info(http_tree_item)")) {
                    while (columns.next()) migrated |= "environment_name".equals(columns.getString("name"));
                }
                assertTrue(migrated, "Startup must migrate an existing table missing environment_name");
                try (var row = sql.executeQuery("SELECT body,environment_name FROM http_tree_item WHERE id=-789")) {
                    assertTrue(row.next()); assertEquals("旧内容", row.getString(1)); assertNull(row.getString(2));
                }
                sql.executeUpdate("UPDATE http_tree_item SET environment_name='测试环境' WHERE id=-789");
                new HttpTreeItemRepositoryImpl();
                try (var row = sql.executeQuery("SELECT environment_name FROM http_tree_item WHERE id=-789")) {
                    assertTrue(row.next()); assertEquals("测试环境", row.getString(1));
                }
            } finally { sql.executeUpdate("DELETE FROM http_tree_item WHERE id=-789"); }
        }
    }
}
