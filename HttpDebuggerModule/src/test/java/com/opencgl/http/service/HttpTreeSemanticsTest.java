package com.opencgl.http.service;

import com.opencgl.http.model.HttpTreeItem;
import com.opencgl.http.repository.HttpTreeItemRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HttpTreeSemanticsTest {
    static class Repository implements HttpTreeItemRepository {
        final List<HttpTreeItem> rows = new ArrayList<>();
        HttpTreeItem saved;
        public List<HttpTreeItem> findAll() { return rows; }
        public Optional<HttpTreeItem> findById(Long id) { return rows.stream().filter(i -> Objects.equals(i.getId(), id)).findFirst(); }
        public List<HttpTreeItem> findByParentId(Long id) { return List.of(); }
        public List<HttpTreeItem> findByNodeType(String type) { return List.of(); }
        public HttpTreeItem save(HttpTreeItem item) { saved = item; return item; }
        public void delete(HttpTreeItem item) {}
        public void deleteById(Long id) {}
        public void deleteWithChildren(Long id) {}
        public void updateSortOrder(Long id, Integer order) {}
        public void updateEnvironment(Long id, String name) { findById(id).orElseThrow().setEnvironmentName(name); }
        public void initializeDatabase() {}
    }
    @Test void explicitLeafFlagIsAuthoritativeAndReadDoesNotWriteLegacyRows() {
        Repository repo = new Repository();
        var folder = new HttpTreeItem(); folder.setId(1L); folder.setIsLeaf(false);
        folder.setNodeType(HttpTreeItem.TYPE_REQUEST); repo.rows.add(folder);
        var service = new HttpTreeService(repo);
        assertFalse(service.queryAll().getFirst().getIsLeaf());
        assertFalse(service.queryById(1L).getIsLeaf());
        assertNull(repo.saved);
    }
    @Test void saveAndAddKeepCompatibilityTypeConsistentWithLeafFlag() {
        Repository repo = new Repository();
        var service = new HttpTreeService(repo);
        var request = new HttpTreeItem(); request.setIsLeaf(true); request.setNodeType(HttpTreeItem.TYPE_FOLDER);
        service.add(request);
        assertEquals(HttpTreeItem.TYPE_REQUEST, repo.saved.getNodeType());
        assertEquals("GET", repo.saved.getMethod());
        request.setIsLeaf(false); service.save(request);
        assertEquals(HttpTreeItem.TYPE_FOLDER, repo.saved.getNodeType());
    }
    @Test void onlyNullLeafMayUseLegacyTypeAsFallback() {
        Repository repo = new Repository();
        var request = new HttpTreeItem(); request.setNodeType(HttpTreeItem.TYPE_REQUEST); repo.rows.add(request);
        assertTrue(new HttpTreeService(repo).queryAll().getFirst().getIsLeaf());
        assertNull(repo.saved);
    }
}
