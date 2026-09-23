package com.opencgl.http.service;

import com.opencgl.base.service.TreeOperateService;
import com.opencgl.base.view.CustomizeTreeItem;
import com.opencgl.http.model.HttpTreeItem;
import com.opencgl.http.repository.HttpTreeItemRepository;
import com.opencgl.http.repository.impl.HttpTreeItemRepositoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

public class HttpTreeService implements TreeOperateService<HttpTreeItem> {
    private static final Logger logger = LoggerFactory.getLogger(HttpTreeService.class);
    private final HttpTreeItemRepository repository;
    
    public HttpTreeService() {
        this(new HttpTreeItemRepositoryImpl());
    }

    HttpTreeService(HttpTreeItemRepository repository) {
        this.repository = java.util.Objects.requireNonNull(repository);
    }
    
    @Override
    public CustomizeTreeItem<HttpTreeItem> add(HttpTreeItem item) {
        normalizeForWrite(item);
        if (Boolean.TRUE.equals(item.getIsLeaf())) {
            if (item.getMethod() == null) item.setMethod("GET");
            if (item.getUrl() == null) item.setUrl("");
            if (item.getTimeout() == null) item.setTimeout(30000);
        }
        return new CustomizeTreeItem<>(repository.save(item));
    }
    
    @Override
    public CustomizeTreeItem<HttpTreeItem> importData(HttpTreeItem item) {
        return add(item);
    }
    
    @Override
    public CustomizeTreeItem<HttpTreeItem> delete(HttpTreeItem item) {
        repository.deleteWithChildren(item.getId());
        return new CustomizeTreeItem<>(item);
    }
    
    @Override
    public CustomizeTreeItem<HttpTreeItem> update(HttpTreeItem item) {
        normalizeForWrite(item);
        repository.save(item);
        return new CustomizeTreeItem<>(item);
    }
    
    @Override
    public void changeToDisplay(HttpTreeItem item) {
        logger.debug("Display changed to: {}", item.getName());
    }
    
    @Override
    public List<HttpTreeItem> queryAll() {
        List<HttpTreeItem> items = repository.findAll();
        items.forEach(this::normalizeForRead);
        return items;
    }
    
    @Override
    public void updatePositionOnly(HttpTreeItem item) {
        repository.updateSortOrder(item.getId(), item.getSortOrder());
    }
    

    
    public HttpTreeItem createFolder(String name, Long parentId) {
        HttpTreeItem folder = new HttpTreeItem();
        folder.setName(name);
        folder.setParentId(parentId);
        folder.setNodeType(HttpTreeItem.TYPE_FOLDER);
        folder.setIsLeaf(false);
        return repository.save(folder);
    }
    
    public HttpTreeItem createRequest(String name, String method, String url, Long parentId) {
        HttpTreeItem request = new HttpTreeItem();
        request.setName(name);
        request.setParentId(parentId);
        request.setNodeType(HttpTreeItem.TYPE_REQUEST);
        request.setIsLeaf(true);
        request.setMethod(method);
        request.setUrl(url);
        request.setTimeout(30000);
        return repository.save(request);
    }
    
    /**
     * 根据ID查询节点
     */
    public HttpTreeItem queryById(Long id) {
        HttpTreeItem item = repository.findById(id).orElse(null);
        if (item != null) normalizeForRead(item);
        return item;
    }
    
    public HttpTreeItem save(HttpTreeItem item) {
        normalizeForWrite(item);
        return repository.save(item);
    }

    public void updateEnvironment(Long id, String name) {
        repository.updateEnvironment(id, name == null || EnvironmentService.DEFAULT_ENV_NAME.equals(name) ? null : name);
    }

    private void normalizeForRead(HttpTreeItem item) {
        if (item.getIsLeaf() == null) {
            item.setIsLeaf(HttpTreeItem.TYPE_REQUEST.equals(item.getNodeType()));
        } else if (item.getNodeType() != null && !item.getNodeType().equals(
                Boolean.TRUE.equals(item.getIsLeaf()) ? HttpTreeItem.TYPE_REQUEST : HttpTreeItem.TYPE_FOLDER)) {
            logger.warn("HTTP tree id={} has conflicting node_type/is_leaf; using is_leaf without rewriting storage", item.getId());
        }
    }

    private void normalizeForWrite(HttpTreeItem item) {
        normalizeForRead(item);
        item.setNodeType(Boolean.TRUE.equals(item.getIsLeaf()) ? HttpTreeItem.TYPE_REQUEST : HttpTreeItem.TYPE_FOLDER);
    }
}
