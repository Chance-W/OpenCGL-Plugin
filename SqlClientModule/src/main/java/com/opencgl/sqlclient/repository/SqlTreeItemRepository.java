package com.opencgl.sqlclient.repository;

import com.opencgl.sqlclient.model.NodeType;
import com.opencgl.sqlclient.model.SqlTreeItem;

import java.util.List;
import java.util.Optional;

/**
 * SQL树节点数据访问接口
 * 
 * @author Chance.W
 */
public interface SqlTreeItemRepository {
    
    /**
     * 初始化数据库表
     */
    void initializeDatabase();

    /**
     * 查询所有节点
     */
    List<SqlTreeItem> findAll();
    
    /**
     * 根据ID查询
     */
    Optional<SqlTreeItem> findById(Long id);
    
    /**
     * 根据父ID查询子节点
     */
    List<SqlTreeItem> findByParentId(Long parentId);
    
    /**
     * 根据节点类型查询
     */
    List<SqlTreeItem> findByNodeType(String nodeType);
    
    /**
     * 保存节点
     */
    SqlTreeItem save(SqlTreeItem item);
    
    /**
     * 批量保存
     */
    void saveAll(List<SqlTreeItem> items);
    
    /**
     * 删除节点
     */
    void delete(SqlTreeItem item);
    
    /**
     * 根据ID删除
     */
    void deleteById(Long id);
    
    /**
     * 删除节点及其所有子节点
     */
    void deleteWithChildren(Long id);
    
    /**
     * 更新排序
     */
    void updateSortOrder(Long id, Integer sortOrder);
    

}
