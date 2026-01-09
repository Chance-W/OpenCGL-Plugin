package com.opencgl.sqlclient.service;

import com.opencgl.base.service.TreeOperateService;
import com.opencgl.base.view.CustomizeTreeItem;
import com.opencgl.sqlclient.model.NodeType;
import com.opencgl.sqlclient.model.SqlTreeItem;
import com.opencgl.sqlclient.repository.SqlTreeItemRepository;
import com.opencgl.sqlclient.repository.impl.SqlTreeItemRepositoryImpl;
import com.opencgl.sqlclient.util.AesEncryptUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * SQL树Service层
 * 实现TreeOperateService接口以集成TreeViewBuilder
 * 
 * @author Chance.W
 */
public class SqlTreeService implements TreeOperateService<SqlTreeItem> {
    
    private static final Logger logger = LoggerFactory.getLogger(SqlTreeService.class);
    private final SqlTreeItemRepository repository;
    
    public SqlTreeService() {
        this.repository = new SqlTreeItemRepositoryImpl();
    }
    
    @Override
    public CustomizeTreeItem<SqlTreeItem> add(SqlTreeItem treeDataDto) {
        // 加密密码
        if (treeDataDto.isConnection() && treeDataDto.getPassword() != null) {
            treeDataDto.setPassword(AesEncryptUtil.encrypt(treeDataDto.getPassword()));
        }
        
        SqlTreeItem saved = repository.save(treeDataDto);
        return new CustomizeTreeItem<>(saved);
    }
    
    @Override
    public CustomizeTreeItem<SqlTreeItem> importData(SqlTreeItem treeDataDto) {
        return add(treeDataDto);
    }
    
    @Override
    public CustomizeTreeItem<SqlTreeItem> delete(SqlTreeItem treeDataDto) {
        repository.deleteWithChildren(treeDataDto.getId());
        return new CustomizeTreeItem<>(treeDataDto);
    }
    
    @Override
    public CustomizeTreeItem<SqlTreeItem> update(SqlTreeItem treeDataDto) {
        // 加密密码
        if (treeDataDto.isConnection() && treeDataDto.getPassword() != null) {
            String pwd = treeDataDto.getPassword();
            if (pwd.length() < 50) {
                treeDataDto.setPassword(AesEncryptUtil.encrypt(pwd));
            }
        }
        
        repository.save(treeDataDto);
        return new CustomizeTreeItem<>(treeDataDto);
    }
    
    @Override
    public void changeToDisplay(SqlTreeItem treeDataDto) {
        // 切换显示（选中节点时的回调）
        logger.debug("Display changed to: {}", treeDataDto.getName());
    }
    
    @Override
    public List<SqlTreeItem> queryAll() {
        List<SqlTreeItem> items = repository.findAll();
        
        // 解密密码
        for (SqlTreeItem item : items) {
            if (item.isConnection() && item.getPassword() != null) {
                item.setPassword(AesEncryptUtil.decrypt(item.getPassword()));
            }
        }
        
        return items;
    }
    
    @Override
    public void updatePositionOnly(SqlTreeItem item) {
        repository.updateSortOrder(item.getId(), item.getSortOrder());
    }
    
    @Override
    public boolean supportAction() {
        return true; // 支持自定义操作（如连接数据库）
    }
    
    @Override
    public String getActionName() {
        return "连接";
    }
    
    @Override
    public void performAction(SqlTreeItem data) {
        if (data.isConnection()) {
            logger.info("Connecting to database: {}", data.getName());
            // TODO: 实现数据库连接逻辑
        }
    }
    
    /**
     * 创建连接组
     */
    public SqlTreeItem createConnectionGroup(String name, Long parentId) {
        SqlTreeItem group = new SqlTreeItem();
        group.setName(name);
        group.setParentId(parentId);
        group.setNodeTypeEnum(NodeType.CONNECTION_GROUP);
        group.setIsLeaf(false);
        return repository.save(group);
    }
    
    /**
     * 创建数据库连接
     */
    public SqlTreeItem createConnection(String name, String host, Integer port, 
                                       String database, String username, String password,
                                       String dbType, Long parentId) {
        SqlTreeItem connection = new SqlTreeItem();
        connection.setName(name);
        connection.setParentId(parentId);
        connection.setNodeTypeEnum(NodeType.CONNECTION);
        connection.setIsLeaf(false);
        connection.setHost(host);
        connection.setPort(port);
        connection.setDatabaseName(database);
        connection.setUsername(username);
        connection.setPassword(AesEncryptUtil.encrypt(password));
        connection.setDbType(dbType);
        return repository.save(connection);
    }

    public void createTableIfAbsent(String name, Long parentId, String dbType) {
        if (repository.findByParentId(parentId).stream().anyMatch(i -> NodeType.TABLE.name().equals(i.getNodeType()) && name.equalsIgnoreCase(i.getName()))) return;
        SqlTreeItem table = new SqlTreeItem(); table.setName(name); table.setParentId(parentId);
        table.setNodeTypeEnum(NodeType.TABLE); table.setIsLeaf(true); table.setDbType(dbType);
        repository.save(table);
    }
    
    /**
     * 根据ID查询
     */
    public Optional<SqlTreeItem> findById(Long id) {
        Optional<SqlTreeItem> itemOpt = repository.findById(id);
        
        // 解密密码
        itemOpt.ifPresent(item -> {
            if (item.isConnection() && item.getPassword() != null) {
                item.setPassword(AesEncryptUtil.decrypt(item.getPassword()));
            }
        });
        
        return itemOpt;
    }
}
