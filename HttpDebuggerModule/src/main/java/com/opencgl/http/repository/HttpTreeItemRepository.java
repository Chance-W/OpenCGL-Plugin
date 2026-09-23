package com.opencgl.http.repository;

import com.opencgl.http.model.HttpTreeItem;

import java.util.List;
import java.util.Optional;

/**
 * HTTP树节点数据访问接口
 */
public interface HttpTreeItemRepository {
    List<HttpTreeItem> findAll();
    Optional<HttpTreeItem> findById(Long id);
    List<HttpTreeItem> findByParentId(Long parentId);
    List<HttpTreeItem> findByNodeType(String nodeType);
    HttpTreeItem save(HttpTreeItem item);
    void delete(HttpTreeItem item);
    void deleteById(Long id);
    void deleteWithChildren(Long id);
    void updateSortOrder(Long id, Integer sortOrder);
    void updateEnvironment(Long id, String environmentName);
    void initializeDatabase();
}
