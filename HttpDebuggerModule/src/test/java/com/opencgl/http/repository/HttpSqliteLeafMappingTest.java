package com.opencgl.http.repository;

import com.opencgl.http.model.HttpTreeItem;
import com.opencgl.http.repository.impl.HttpTreeItemRepositoryImpl;
import java.sql.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HttpSqliteLeafMappingTest {
    @Test void sqliteTextAndIntegerLeafFlagsAreReadWithoutLosingType() throws Exception {
        // Skip repository constructor: it initializes the user's DB. Exercise real mapper against SQLite memory.
        var repository = mock(HttpTreeItemRepositoryImpl.class, CALLS_REAL_METHODS);
        var mapper = HttpTreeItemRepositoryImpl.class.getDeclaredMethod("mapItem", ResultSet.class);
        mapper.setAccessible(true);
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
            for (String flag : new String[]{"'true'", "1", "'false'", "0", "NULL"}) {
                String columns = "1 AS id,0 AS parent_id,'request' AS name," + flag + " AS is_leaf,0 AS sort_order,'REQUEST' AS node_type,NULL AS environment_name";
                for (String column : new String[]{"icon_name", "description", "method", "url", "headers", "params", "body", "body_type", "auth_config", "hook_script", "timeout", "ssl_verification", "follow_redirects", "client_cert_path", "client_cert_pass", "server_cert_path", "server_cert_pass", "last_response", "last_status_code", "created_at", "updated_at"})
                    columns += ",NULL AS " + column;
                try (var rs = statement.executeQuery("SELECT " + columns)) {
                    assertTrue(rs.next());
                    var item = (HttpTreeItem)mapper.invoke(repository, rs);
                    Boolean expected = flag.equals("NULL") ? null : flag.equals("1") || flag.equals("'true'");
                    assertEquals(expected, item.getIsLeaf(), flag);
                    assertEquals("REQUEST", item.getNodeType(), "Reading must not rewrite node_type: " + flag);
                }
            }
        }
    }
}
