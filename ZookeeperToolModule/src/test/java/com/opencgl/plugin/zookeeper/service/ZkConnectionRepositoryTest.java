package com.opencgl.plugin.zookeeper.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ZkConnectionRepositoryTest {
    @TempDir Path directory;

    @Test void savesGroupsAndClusterConnectionsAcrossReopening() {
        var db = directory.resolve("nested/data.db");
        var repository = new ZkConnectionRepository(db);
        var group = repository.create(0L, false, "测试环境");
        var connection = repository.create(group.getId(), true, "集群");
        connection.setServers("zk-a:2181,zk-b:2181/app");
        connection.setTimeoutMs(12000);
        repository.save(connection);
        var rows = new ZkConnectionRepository(db).load();
        assertEquals(2, rows.size());
        var saved = rows.stream().filter(n -> Boolean.TRUE.equals(n.getIsLeaf())).findFirst().orElseThrow();
        assertEquals(group.getId(), saved.getParentId());
        assertEquals("集群", saved.getName());
        assertEquals("zk-a:2181,zk-b:2181/app", saved.getServers());
        assertEquals(12000, saved.getTimeoutMs());
    }

    @Test void copyingIsIndependentAndDeletingGroupOnlyDeletesItsLocalSubtree() {
        var repository = new ZkConnectionRepository(directory.resolve("data.db"));
        var group = repository.create(0L, false, "group");
        var child = repository.create(group.getId(), false, "child");
        var connection = repository.create(child.getId(), true, "one");
        var outside = repository.create(0L, true, "outside");
        var copy = repository.copy(connection);
        copy.setServers("other:2181"); repository.save(copy);
        assertNotEquals(connection.getId(), copy.getId());
        assertEquals("localhost:2181", repository.load().stream().filter(n -> n.getId().equals(connection.getId())).findFirst().orElseThrow().getServers());
        repository.delete(group.getId());
        assertEquals(java.util.List.of(outside.getId()), repository.load().stream().map(ZkConnection::getId).toList());
    }

    @Test void invalidConfigAndConnectionAsParentCannotBeSaved() {
        var repository = new ZkConnectionRepository(directory.resolve("data.db"));
        var connection = repository.create(0L, true, "one");
        assertThrows(IllegalArgumentException.class, () -> repository.create(connection.getId(), true, "nested"));
        connection.setServers(" ");
        assertThrows(IllegalArgumentException.class, () -> repository.save(connection));
        connection.setServers("localhost:2181"); connection.setTimeoutMs(0);
        assertThrows(IllegalArgumentException.class, () -> repository.save(connection));
        assertEquals(5000, repository.load().get(0).getTimeoutMs());
    }
}
