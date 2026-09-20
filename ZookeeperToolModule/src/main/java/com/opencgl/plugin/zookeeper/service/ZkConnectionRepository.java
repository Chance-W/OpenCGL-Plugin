package com.opencgl.plugin.zookeeper.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Separate table in the host database. All mutations concern local profiles only. */
public final class ZkConnectionRepository {
    private final String url;
    public ZkConnectionRepository(Path database) {
        url = "jdbc:sqlite:" + database.toAbsolutePath();
        try {
            Files.createDirectories(database.toAbsolutePath().getParent());
            try (var c = open(); var s = c.createStatement()) {
                s.executeUpdate("CREATE TABLE IF NOT EXISTS ZOOKEEPER_CONNECTION_TREE (id INTEGER PRIMARY KEY AUTOINCREMENT, parent_id INTEGER NOT NULL, name TEXT NOT NULL, is_leaf INTEGER NOT NULL, servers TEXT NOT NULL, timeout_ms INTEGER NOT NULL, sort_order INTEGER NOT NULL)");
            }
        } catch (Exception e) { throw failure(e); }
    }
    private Connection open() throws SQLException {
        var c = DriverManager.getConnection(url);
        try (var s = c.createStatement()) { s.execute("PRAGMA busy_timeout=1500"); }
        return c;
    }
    private IllegalStateException failure(Exception e) {
        return new IllegalStateException("ZooKeeper connection storage: " + e.getMessage(), e);
    }
    public List<ZkConnection> load() {
        try (var c = open(); var s = c.createStatement(); var r = s.executeQuery("SELECT * FROM ZOOKEEPER_CONNECTION_TREE ORDER BY sort_order,id")) {
            var result = new ArrayList<ZkConnection>();
            while (r.next()) {
                var n = new ZkConnection();
                n.setId(r.getLong("id")); n.setParentId(r.getLong("parent_id"));
                n.setName(r.getString("name")); n.setIsLeaf(r.getBoolean("is_leaf"));
                n.setServers(r.getString("servers")); n.setTimeoutMs(r.getInt("timeout_ms"));
                n.setSortOrder(r.getInt("sort_order")); result.add(n);
            }
            return result;
        } catch (SQLException e) { throw failure(e); }
    }
    public ZkConnection create(Long parent, boolean leaf, String name) {
        var n = new ZkConnection(); n.setParentId(parent == null ? 0L : parent);
        n.setName(name); n.setIsLeaf(leaf); n.setSortOrder(load().size()); save(n); return n;
    }
    public void save(ZkConnection n) {
        if (n.getName() == null || n.getName().isBlank()) throw new IllegalArgumentException("连接/分组名称不能为空");
        if (Boolean.TRUE.equals(n.getIsLeaf()) && (n.getServers() == null || n.getServers().isBlank() || n.getTimeoutMs() <= 0))
            throw new IllegalArgumentException("请填写服务器地址，连接超时必须大于 0");
        long parent = n.getParentId() == null ? 0 : n.getParentId();
        if (parent != 0 && load().stream().noneMatch(p -> p.getId() == parent && !Boolean.TRUE.equals(p.getIsLeaf())))
            throw new IllegalArgumentException("连接只能放在根目录或分组下");
        if (n.getId() != null && n.getId() == parent) throw new IllegalArgumentException("不能将节点放入自身");
        String sql = n.getId() == null
                ? "INSERT INTO ZOOKEEPER_CONNECTION_TREE(parent_id,name,is_leaf,servers,timeout_ms,sort_order) VALUES(?,?,?,?,?,?)"
                : "UPDATE ZOOKEEPER_CONNECTION_TREE SET parent_id=?,name=?,is_leaf=?,servers=?,timeout_ms=?,sort_order=? WHERE id=?";
        try (var c = open(); var s = c.prepareStatement(sql)) {
            s.setLong(1, parent); s.setString(2, n.getName().trim()); s.setBoolean(3, Boolean.TRUE.equals(n.getIsLeaf()));
            s.setString(4, n.getServers().trim()); s.setInt(5, n.getTimeoutMs()); s.setInt(6, n.getSortOrder() == null ? 0 : n.getSortOrder());
            if (n.getId() != null) s.setLong(7, n.getId());
            s.executeUpdate();
            if (n.getId() == null) try (var q = c.createStatement(); var r = q.executeQuery("SELECT last_insert_rowid()")) {
                r.next(); n.setId(r.getLong(1));
            }
        } catch (SQLException e) { throw failure(e); }
    }
    public ZkConnection copy(ZkConnection source) {
        var copy = source.snapshot(); copy.setId(null); copy.setName(source.getName() + " Copy");
        copy.setSortOrder(load().size()); save(copy); return copy;
    }
    public void delete(Long id) {
        if (id == null || id == 0) return;
        try (var c = open(); var s = c.prepareStatement("WITH RECURSIVE subtree(id) AS (SELECT id FROM ZOOKEEPER_CONNECTION_TREE WHERE id=? UNION SELECT n.id FROM ZOOKEEPER_CONNECTION_TREE n JOIN subtree p ON n.parent_id=p.id) DELETE FROM ZOOKEEPER_CONNECTION_TREE WHERE id IN (SELECT id FROM subtree)")) {
            s.setLong(1, id); s.executeUpdate();
        } catch (SQLException e) { throw failure(e); }
    }
}
