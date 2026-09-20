package com.opencgl.solace.persistence;

import com.opencgl.solace.model.SolaceNodeType;
import com.opencgl.solace.model.SolaceTreeNode;
import com.opencgl.solace.model.SolaceWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SolaceRepositoryMigrationTest {
    @TempDir Path tempDir;

    @Test
    void importsLegacyJsonOnlyWhenSqliteIsEmpty() throws Exception {
        SecretProtector protector = plainProtector();
        var legacy = new JsonSolaceConfigurationRepository(tempDir.resolve("workspace.json"), protector);
        var sqlite = new SqliteSolaceConfigurationRepository(tempDir.resolve("data.db"), protector);
        legacy.save(workspace("旧 JSON 分组"));

        SolaceRepositoryMigration.importLegacyJsonWhenEmpty(sqlite, legacy);
        assertEquals("旧 JSON 分组", sqlite.load().getNodes().getFirst().getName());

        sqlite.save(workspace("SQLite 新数据"));
        SolaceRepositoryMigration.importLegacyJsonWhenEmpty(sqlite, legacy);
        assertEquals("SQLite 新数据", sqlite.load().getNodes().getFirst().getName());
    }

    private SolaceWorkspace workspace(String name) {
        SolaceTreeNode node = new SolaceTreeNode();
        node.setId(1L);
        node.setParentId(0L);
        node.setName(name);
        node.setNodeType(SolaceNodeType.DIRECTORY);
        node.setIsLeaf(false);
        node.setSortOrder(0);
        SolaceWorkspace workspace = new SolaceWorkspace();
        workspace.setNodes(List.of(node));
        return workspace;
    }

    private SecretProtector plainProtector() {
        return new SecretProtector() {
            @Override public String protect(String plainText) { return plainText; }
            @Override public String unprotect(String protectedText) { return protectedText; }
        };
    }
}
