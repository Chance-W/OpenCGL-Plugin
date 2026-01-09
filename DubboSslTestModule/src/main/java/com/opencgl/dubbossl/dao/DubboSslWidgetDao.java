package com.opencgl.dubbossl.dao;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencgl.base.model.BaseDataDto;
import com.opencgl.base.utils.SqliteUtil;
import com.opencgl.dubbossl.model.DubboSslTreeItem;

/**
 * Dubbo SSL 数据访问层
 * 
 * @author Chance.W
 * 增加 HOOK_SCRIPT 字段支持 Hook 脚本持久化
 */
@SuppressWarnings("unused")
public class DubboSslWidgetDao {
    private static final Logger logger = LoggerFactory.getLogger(DubboSslWidgetDao.class);
    public static final String DUBBO_ENV_CONFIG_TABLE = "DUBBO_ENV_CONFIG";
    public static final String DUBBO_SSL_TREE_ITEM_TABLE = "DUBBO_SSL_TREE_ITEM";

    public DubboSslWidgetDao() {
        checkTable();
    }

    private void checkTable() {
        try {
            if (!SqliteUtil.checkTableExist(DUBBO_SSL_TREE_ITEM_TABLE)) {
                SqliteUtil.update("CREATE TABLE " + DUBBO_SSL_TREE_ITEM_TABLE + " (" +
                    "ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "PARENT_ID INTEGER," +
                    "NAME VARCHAR(150) NOT NULL," +
                    "IS_LEAF BOOLEAN NOT NULL," +
                    "ENVNAME VARCHAR," +
                    "INTERFACEINFO VARCHAR," +
                    "METHODINFO VARCHAR," +
                    "IS_SELECTED BOOLEAN default(0)," +
                    "REQUEST_TYPE VARCHAR," +
                    "INPUTTEXT VARCHAR," +
                    "SORT_ORDER INTEGER DEFAULT 0," +
                    "HOOK_SCRIPT VARCHAR" +
                    ")");
                logger.info("创建表 {} 成功", DUBBO_SSL_TREE_ITEM_TABLE);
            } else {
                // 检查是否有SORT_ORDER字段，没有则添加
                try {
                    SqliteUtil.query("SELECT SORT_ORDER FROM " + DUBBO_SSL_TREE_ITEM_TABLE + " LIMIT 1");
                } catch (Exception e) {
                    SqliteUtil.update("ALTER TABLE " + DUBBO_SSL_TREE_ITEM_TABLE + " ADD COLUMN SORT_ORDER INTEGER DEFAULT 0;");
                    logger.info("为表 {} 添加 SORT_ORDER 字段成功", DUBBO_SSL_TREE_ITEM_TABLE);
                }
                
                // 检查是否有HOOK_SCRIPT字段，没有则添加
                try {
                    SqliteUtil.query("SELECT HOOK_SCRIPT FROM " + DUBBO_SSL_TREE_ITEM_TABLE + " LIMIT 1");
                } catch (Exception e) {
                    SqliteUtil.update("ALTER TABLE " + DUBBO_SSL_TREE_ITEM_TABLE + " ADD COLUMN HOOK_SCRIPT VARCHAR;");
                    logger.info("为表 {} 添加 HOOK_SCRIPT 字段成功", DUBBO_SSL_TREE_ITEM_TABLE);
                }
            }

            // check DUBBO_ENV_CONFIG table
            if (!SqliteUtil.checkTableExist(DUBBO_ENV_CONFIG_TABLE)) {
                SqliteUtil.update("CREATE TABLE " + DUBBO_ENV_CONFIG_TABLE + " (" +
                    "ENV_NAME VARCHAR PRIMARY KEY," +
                    "REGISTRY_ADDRESS VARCHAR," +
                    "REGISTRY_GROUP VARCHAR," +
                    "API_PACKAGE_PATH VARCHAR," +
                    "SERVICE_DATA TEXT" +
                    ")");
                logger.info("创建表 {} 成功", DUBBO_ENV_CONFIG_TABLE);
            }
        } catch (Exception e) {
            logger.error("检查/创建表失败", e);
        }
    }

    /**
     * 查询所有数据，按SORT_ORDER排序
     */
    public List<DubboSslTreeItem> queryAllData() throws Exception {
        return SqliteUtil.queryForList(
            "SELECT ID, PARENT_ID, NAME, IS_LEAF, ENVNAME, INTERFACEINFO, METHODINFO, IS_SELECTED, REQUEST_TYPE, INPUTTEXT, SORT_ORDER, HOOK_SCRIPT FROM " + DUBBO_SSL_TREE_ITEM_TABLE + " ORDER BY SORT_ORDER, ID", 
            DubboSslTreeItem.class);
    }

    public Long insertData(DubboSslTreeItem dto) throws Exception {
        BaseDataDto base = dto;
        return SqliteUtil.insert(
            "INSERT INTO " + DUBBO_SSL_TREE_ITEM_TABLE + "(PARENT_ID, NAME, IS_LEAF, ENVNAME, INTERFACEINFO, METHODINFO, IS_SELECTED, REQUEST_TYPE, INPUTTEXT, SORT_ORDER, HOOK_SCRIPT) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            base.getParentId(),
            base.getName(),
            base.getIsLeaf(),
            dto.getEnvName(),
            dto.getInterfaceInfo(),
            dto.getMethodInfo(),
            dto.getIsSelected(),
            dto.getRequestType(),
            dto.getInputText(),
            base.getSortOrder() != null ? base.getSortOrder() : 0,
            dto.getHookScript());
    }

    public void delLevelData(DubboSslTreeItem dto) throws Exception {
        SqliteUtil.update("DELETE FROM " + DUBBO_SSL_TREE_ITEM_TABLE + " WHERE ID = ?", dto.getId());
    }

    public void updateLevelData(Long newParentId, DubboSslTreeItem where) throws Exception {
        SqliteUtil.update("UPDATE " + DUBBO_SSL_TREE_ITEM_TABLE + " SET PARENT_ID= ? WHERE ID = ?", newParentId, where.getId());
    }

    public void updateData(DubboSslTreeItem dto) throws Exception {
        BaseDataDto base = dto;
        SqliteUtil.update(
            "UPDATE " + DUBBO_SSL_TREE_ITEM_TABLE + " SET NAME=?, PARENT_ID=?, ENVNAME=?, INTERFACEINFO=?, METHODINFO=?, IS_SELECTED=?, REQUEST_TYPE=?, INPUTTEXT=?, SORT_ORDER=?, HOOK_SCRIPT=? WHERE ID = ?",
            base.getName(),
            base.getParentId(),
            dto.getEnvName(),
            dto.getInterfaceInfo(),
            dto.getMethodInfo(),
            dto.getIsSelected(),
            dto.getRequestType(),
            dto.getInputText(),
            base.getSortOrder() != null ? base.getSortOrder() : 0,
            dto.getHookScript(),
            base.getId());
    }

    public DubboSslTreeItem queryData(DubboSslTreeItem dto) throws Exception {
        List<DubboSslTreeItem> result = SqliteUtil.queryForList(
            "SELECT ENVNAME, INTERFACEINFO, METHODINFO, IS_SELECTED, REQUEST_TYPE, INPUTTEXT, SORT_ORDER, HOOK_SCRIPT FROM " + DUBBO_SSL_TREE_ITEM_TABLE + " WHERE ID = ?",
            DubboSslTreeItem.class,
            dto.getId());
        return result.isEmpty() ? null : result.get(0);
    }

    /**
     * 只更新位置信息（拖拽时使用）
     */
    public void updatePositionOnly(DubboSslTreeItem dto) throws Exception {
        SqliteUtil.update(
            "UPDATE " + DUBBO_SSL_TREE_ITEM_TABLE + " SET PARENT_ID = ?, SORT_ORDER = ? WHERE ID = ?",
            dto.getParentId(),
            dto.getSortOrder() != null ? dto.getSortOrder() : 0,
            dto.getId());
    }

    // ================= DUBBO ENV CONFIG METHODS =================

    public List<com.opencgl.dubbossl.model.DubboEnvConfig> queryAllEnvConfig() throws Exception {
        return SqliteUtil.queryForList(
            "SELECT ENV_NAME as envName, REGISTRY_ADDRESS as registryAddress, REGISTRY_GROUP as registryGroup, API_PACKAGE_PATH as apiPackagePath, SERVICE_DATA as serviceData FROM " + DUBBO_ENV_CONFIG_TABLE,
            com.opencgl.dubbossl.model.DubboEnvConfig.class);
    }

    public com.opencgl.dubbossl.model.DubboEnvConfig queryEnvConfig(String envName) throws Exception {
        List<com.opencgl.dubbossl.model.DubboEnvConfig> list = SqliteUtil.queryForList(
            "SELECT ENV_NAME as envName, REGISTRY_ADDRESS as registryAddress, REGISTRY_GROUP as registryGroup, API_PACKAGE_PATH as apiPackagePath, SERVICE_DATA as serviceData FROM " + DUBBO_ENV_CONFIG_TABLE + " WHERE ENV_NAME = ?",
            com.opencgl.dubbossl.model.DubboEnvConfig.class,
            envName);
        return list.isEmpty() ? null : list.get(0);
    }

    public void insertEnvConfig(com.opencgl.dubbossl.model.DubboEnvConfig config) throws Exception {
        SqliteUtil.insert(
            "INSERT INTO " + DUBBO_ENV_CONFIG_TABLE + "(ENV_NAME, REGISTRY_ADDRESS, REGISTRY_GROUP, API_PACKAGE_PATH, SERVICE_DATA) VALUES (?, ?, ?, ?, ?)",
            config.getEnvName(),
            config.getRegistryAddress(),
            config.getRegistryGroup(),
            config.getApiPackagePath(),
            config.getServiceData());
    }

    public void updateEnvConfig(com.opencgl.dubbossl.model.DubboEnvConfig config) throws Exception {
        SqliteUtil.update(
            "UPDATE " + DUBBO_ENV_CONFIG_TABLE + " SET REGISTRY_ADDRESS=?, REGISTRY_GROUP=?, API_PACKAGE_PATH=?, SERVICE_DATA=? WHERE ENV_NAME=?",
            config.getRegistryAddress(),
            config.getRegistryGroup(),
            config.getApiPackagePath(),
            config.getServiceData(),
            config.getEnvName());
    }

    public void deleteEnvConfig(String envName) throws Exception {
        SqliteUtil.update("DELETE FROM " + DUBBO_ENV_CONFIG_TABLE + " WHERE ENV_NAME = ?", envName);
    }
}
