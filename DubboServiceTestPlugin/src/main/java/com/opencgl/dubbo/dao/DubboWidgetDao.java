package com.opencgl.dubbo.dao;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencgl.base.model.BaseDataDto;
import com.opencgl.base.utils.SqliteUtil;
import com.opencgl.dubbo.model.DubboTreeItem;


/**
 * @author Chance.W
 * 
 * 增加 SORT_ORDER 字段支持拖拽排序
 * 增加 HOOK_SCRIPT 字段支持 Hook 脚本持久化
 */
@SuppressWarnings("unused")
public class DubboWidgetDao {
    private static final Logger logger = LoggerFactory.getLogger(DubboWidgetDao.class);
    public static final String DUBBO_ENV_CONFIG_TABLE = "DUBBO_ENV_CONFIG";
    public static final String DUBBO_TREE_ITEM_TABLE = "DUBBO_TREE_ITEM";
    public static final String DUBBO_HISTORY_TABLE = "DUBBO_HISTORY";

    public DubboWidgetDao() {
        checkTable();
    }

    private void checkTable() {
        try {
            if (!SqliteUtil.checkTableExist(DUBBO_TREE_ITEM_TABLE)) {
                // 新建表需包含完整字段，与下方旧表升级后的结构保持一致
                SqliteUtil.update("CREATE TABLE " + DUBBO_TREE_ITEM_TABLE + " (" +
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
                    "HOOK_SCRIPT VARCHAR," +
                    "VERSION VARCHAR," +
                    "ATTACHMENTS VARCHAR," +
                    "TIMEOUT INTEGER," +
                    "RETRIES INTEGER," +
                    "D_GROUP VARCHAR," +
                    "DUBBO_GROUP VARCHAR," +
                    "TLS_ENABLE BOOLEAN," +
                    "MUTUAL_AUTH BOOLEAN," +
                    "CLIENT_CERT VARCHAR," +
                    "CLIENT_KEY VARCHAR," +
                    "KEY_PASSWORD VARCHAR," +
                    "CA_CERT VARCHAR," +
                    "PROVIDER_URL VARCHAR" +
                    ")");
                logger.info("创建表 {} 成功", DUBBO_TREE_ITEM_TABLE);
            } else {
                // 检查是否有SORT_ORDER字段，没有则添加
                try {
                    SqliteUtil.query("SELECT SORT_ORDER FROM " + DUBBO_TREE_ITEM_TABLE + " LIMIT 1");
                } catch (Exception e) {
                    SqliteUtil.update("ALTER TABLE " + DUBBO_TREE_ITEM_TABLE + " ADD COLUMN SORT_ORDER INTEGER DEFAULT 0;");
                    logger.info("为表 {} 添加 SORT_ORDER 字段成功", DUBBO_TREE_ITEM_TABLE);
                }
                
                // 检查是否有HOOK_SCRIPT字段，没有则添加
                try {
                    SqliteUtil.query("SELECT HOOK_SCRIPT FROM " + DUBBO_TREE_ITEM_TABLE + " LIMIT 1");
                } catch (Exception e) {
                    SqliteUtil.update("ALTER TABLE " + DUBBO_TREE_ITEM_TABLE + " ADD COLUMN HOOK_SCRIPT VARCHAR;");
                    logger.info("为表 {} 添加 HOOK_SCRIPT 字段成功", DUBBO_TREE_ITEM_TABLE);
                }

                // 检查是否有 VERSION 字段，没有则添加
                try {
                    SqliteUtil.query("SELECT VERSION FROM " + DUBBO_TREE_ITEM_TABLE + " LIMIT 1");
                } catch (Exception e) {
                    SqliteUtil.update("ALTER TABLE " + DUBBO_TREE_ITEM_TABLE + " ADD COLUMN VERSION VARCHAR;");
                    logger.info("为表 {} 添加 VERSION 字段成功", DUBBO_TREE_ITEM_TABLE);
                }

                // 检查是否有 ATTACHMENTS 字段，没有则添加
                try {
                    SqliteUtil.query("SELECT ATTACHMENTS FROM " + DUBBO_TREE_ITEM_TABLE + " LIMIT 1");
                } catch (Exception e) {
                    SqliteUtil.update("ALTER TABLE " + DUBBO_TREE_ITEM_TABLE + " ADD COLUMN ATTACHMENTS VARCHAR;");
                    logger.info("为表 {} 添加 ATTACHMENTS 字段成功", DUBBO_TREE_ITEM_TABLE);
                }

                // Phase 12: New fields for persistence
                try { SqliteUtil.query("SELECT TIMEOUT FROM " + DUBBO_TREE_ITEM_TABLE + " LIMIT 1"); } 
                catch (Exception e) { SqliteUtil.update("ALTER TABLE " + DUBBO_TREE_ITEM_TABLE + " ADD COLUMN TIMEOUT INTEGER;"); }

                try { SqliteUtil.query("SELECT RETRIES FROM " + DUBBO_TREE_ITEM_TABLE + " LIMIT 1"); } 
                catch (Exception e) { SqliteUtil.update("ALTER TABLE " + DUBBO_TREE_ITEM_TABLE + " ADD COLUMN RETRIES INTEGER;"); }

                try { SqliteUtil.query("SELECT D_GROUP FROM " + DUBBO_TREE_ITEM_TABLE + " LIMIT 1"); } 
                catch (Exception e) { SqliteUtil.update("ALTER TABLE " + DUBBO_TREE_ITEM_TABLE + " ADD COLUMN D_GROUP VARCHAR;"); }

                // 自动升级：添加 DUBBO_GROUP (Dubbo RPC 服务分组) 列
                try { SqliteUtil.query("SELECT DUBBO_GROUP FROM " + DUBBO_TREE_ITEM_TABLE + " LIMIT 1"); }
                catch (Exception e) { SqliteUtil.update("ALTER TABLE " + DUBBO_TREE_ITEM_TABLE + " ADD COLUMN DUBBO_GROUP VARCHAR;"); logger.info("为表 {} 添加 DUBBO_GROUP 字段成功", DUBBO_TREE_ITEM_TABLE); }

                // Phase 17: TLS Support
                // Check if TLS_ENABLE column exists
                try { SqliteUtil.query("SELECT TLS_ENABLE FROM " + DUBBO_TREE_ITEM_TABLE + " LIMIT 1"); } 
                catch (Exception e) { SqliteUtil.update("ALTER TABLE " + DUBBO_TREE_ITEM_TABLE + " ADD COLUMN TLS_ENABLE BOOLEAN;"); }

                try { SqliteUtil.query("SELECT MUTUAL_AUTH FROM " + DUBBO_TREE_ITEM_TABLE + " LIMIT 1"); } 
                catch (Exception e) { SqliteUtil.update("ALTER TABLE " + DUBBO_TREE_ITEM_TABLE + " ADD COLUMN MUTUAL_AUTH BOOLEAN;"); }

                try { SqliteUtil.query("SELECT CLIENT_CERT FROM " + DUBBO_TREE_ITEM_TABLE + " LIMIT 1"); } 
                catch (Exception e) { SqliteUtil.update("ALTER TABLE " + DUBBO_TREE_ITEM_TABLE + " ADD COLUMN CLIENT_CERT VARCHAR;"); }

                try { SqliteUtil.query("SELECT CLIENT_KEY FROM " + DUBBO_TREE_ITEM_TABLE + " LIMIT 1"); } 
                catch (Exception e) { SqliteUtil.update("ALTER TABLE " + DUBBO_TREE_ITEM_TABLE + " ADD COLUMN CLIENT_KEY VARCHAR;"); }

                try { SqliteUtil.query("SELECT KEY_PASSWORD FROM " + DUBBO_TREE_ITEM_TABLE + " LIMIT 1"); } 
                catch (Exception e) { SqliteUtil.update("ALTER TABLE " + DUBBO_TREE_ITEM_TABLE + " ADD COLUMN KEY_PASSWORD VARCHAR;"); }

                try { SqliteUtil.query("SELECT CA_CERT FROM " + DUBBO_TREE_ITEM_TABLE + " LIMIT 1"); } 
                catch (Exception e) { SqliteUtil.update("ALTER TABLE " + DUBBO_TREE_ITEM_TABLE + " ADD COLUMN CA_CERT VARCHAR;"); }

                try { SqliteUtil.query("SELECT PROVIDER_URL FROM " + DUBBO_TREE_ITEM_TABLE + " LIMIT 1"); }
                catch (Exception e) { SqliteUtil.update("ALTER TABLE " + DUBBO_TREE_ITEM_TABLE + " ADD COLUMN PROVIDER_URL VARCHAR;"); }

            }

            // check DUBBO_ENV_CONFIG_TABLE
            if (!SqliteUtil.checkTableExist(DUBBO_ENV_CONFIG_TABLE)) {
                SqliteUtil.update("CREATE TABLE " + DUBBO_ENV_CONFIG_TABLE + " (" +
                    "ENV_NAME VARCHAR PRIMARY KEY," +
                    "REGISTRY_ADDRESS VARCHAR," +
                    "REGISTRY_GROUP VARCHAR," +
                    "ZK_GROUP VARCHAR," +
                    "API_PACKAGE_PATH VARCHAR," +
                    "SERVICE_DATA TEXT" +
                    ")");
                logger.info("创建表 {} 成功", DUBBO_ENV_CONFIG_TABLE);
            } else {
                // 自动升级：添加 ZK_GROUP 列
                try { SqliteUtil.query("SELECT ZK_GROUP FROM " + DUBBO_ENV_CONFIG_TABLE + " LIMIT 1"); }
                catch (Exception e) { SqliteUtil.update("ALTER TABLE " + DUBBO_ENV_CONFIG_TABLE + " ADD COLUMN ZK_GROUP VARCHAR;"); logger.info("为表 {} 添加 ZK_GROUP 字段成功", DUBBO_ENV_CONFIG_TABLE); }
            }

            // check DUBBO_HISTORY_TABLE
            if (!SqliteUtil.checkTableExist(DUBBO_HISTORY_TABLE)) {
                 SqliteUtil.update("CREATE TABLE " + DUBBO_HISTORY_TABLE + " (" +
                     "ID VARCHAR PRIMARY KEY," +
                     "INTERFACE_NAME VARCHAR," +
                     "METHOD_NAME VARCHAR," +
                     "PARAM_TYPE VARCHAR," +
                     "ADDRESS VARCHAR," +
                     "TIMESTAMP INTEGER," +
                     "STATUS VARCHAR," +
                     "SUMMARY VARCHAR," +
                     "VERSION VARCHAR," +
                     "REQUEST_TYPE VARCHAR," +
                     "ENV_NAME VARCHAR," +
                     "D_GROUP VARCHAR," + // ZK 分组
                     "DUBBO_GROUP VARCHAR," + // Dubbo RPC 服务分组
                     "TIMEOUT INTEGER," +
                     "RETRIES INTEGER," +
                     "HOOK_SCRIPT VARCHAR," +
                     "INPUT_TEXT VARCHAR," +
                     "OUTPUT_TEXT VARCHAR," +
                     "ATTACHMENTS VARCHAR," +
                     "TLS_ENABLE BOOLEAN," +
                     "CLIENT_CERT VARCHAR," +
                     "CLIENT_KEY VARCHAR," +
                     "KEY_PASSWORD VARCHAR," +
                     "CA_CERT VARCHAR," +
                     "PROVIDER_URL VARCHAR" +
                     ")");
                logger.info("创建表 {} 成功", DUBBO_HISTORY_TABLE);
            } else {
                // Check for new columns
                try {
                    SqliteUtil.query("SELECT ENV_NAME FROM " + DUBBO_HISTORY_TABLE + " LIMIT 1");
                } catch (Exception e) {
                    SqliteUtil.update("ALTER TABLE " + DUBBO_HISTORY_TABLE + " ADD COLUMN ENV_NAME VARCHAR;");
                    SqliteUtil.update("ALTER TABLE " + DUBBO_HISTORY_TABLE + " ADD COLUMN INPUT_TEXT VARCHAR;");
                }
                
                try {
                    SqliteUtil.query("SELECT OUTPUT_TEXT FROM " + DUBBO_HISTORY_TABLE + " LIMIT 1");
                } catch (Exception e) {
                    SqliteUtil.update("ALTER TABLE " + DUBBO_HISTORY_TABLE + " ADD COLUMN OUTPUT_TEXT VARCHAR;");
                }

                // New fields for Phase 12
                try { SqliteUtil.query("SELECT D_GROUP FROM " + DUBBO_HISTORY_TABLE + " LIMIT 1"); } 
                catch (Exception e) { SqliteUtil.update("ALTER TABLE " + DUBBO_HISTORY_TABLE + " ADD COLUMN D_GROUP VARCHAR;"); }
                
                try { SqliteUtil.query("SELECT TIMEOUT FROM " + DUBBO_HISTORY_TABLE + " LIMIT 1"); } 
                catch (Exception e) { SqliteUtil.update("ALTER TABLE " + DUBBO_HISTORY_TABLE + " ADD COLUMN TIMEOUT INTEGER;"); }
                
                try { SqliteUtil.query("SELECT RETRIES FROM " + DUBBO_HISTORY_TABLE + " LIMIT 1"); } 
                catch (Exception e) { SqliteUtil.update("ALTER TABLE " + DUBBO_HISTORY_TABLE + " ADD COLUMN RETRIES INTEGER;"); }
                
                try { SqliteUtil.query("SELECT HOOK_SCRIPT FROM " + DUBBO_HISTORY_TABLE + " LIMIT 1"); } 
                catch (Exception e) { SqliteUtil.update("ALTER TABLE " + DUBBO_HISTORY_TABLE + " ADD COLUMN HOOK_SCRIPT VARCHAR;"); }

                // Attachments for History
                try { SqliteUtil.query("SELECT ATTACHMENTS FROM " + DUBBO_HISTORY_TABLE + " LIMIT 1"); } 
                catch (Exception e) { SqliteUtil.update("ALTER TABLE " + DUBBO_HISTORY_TABLE + " ADD COLUMN ATTACHMENTS VARCHAR;"); }

                // DUBBO_GROUP for History (Dubbo RPC service group)
                try { SqliteUtil.query("SELECT DUBBO_GROUP FROM " + DUBBO_HISTORY_TABLE + " LIMIT 1"); } 
                catch (Exception e) { SqliteUtil.update("ALTER TABLE " + DUBBO_HISTORY_TABLE + " ADD COLUMN DUBBO_GROUP VARCHAR;"); logger.info("为表 {} 添加 DUBBO_GROUP 字段成功", DUBBO_HISTORY_TABLE); }

                // Phase 23: TLS Support for History
                try { SqliteUtil.query("SELECT TLS_ENABLE FROM " + DUBBO_HISTORY_TABLE + " LIMIT 1"); } 
                catch (Exception e) { SqliteUtil.update("ALTER TABLE " + DUBBO_HISTORY_TABLE + " ADD COLUMN TLS_ENABLE BOOLEAN;"); }

                try { SqliteUtil.query("SELECT CLIENT_CERT FROM " + DUBBO_HISTORY_TABLE + " LIMIT 1"); } 
                catch (Exception e) { SqliteUtil.update("ALTER TABLE " + DUBBO_HISTORY_TABLE + " ADD COLUMN CLIENT_CERT VARCHAR;"); }

                try { SqliteUtil.query("SELECT CLIENT_KEY FROM " + DUBBO_HISTORY_TABLE + " LIMIT 1"); } 
                catch (Exception e) { SqliteUtil.update("ALTER TABLE " + DUBBO_HISTORY_TABLE + " ADD COLUMN CLIENT_KEY VARCHAR;"); }

                try { SqliteUtil.query("SELECT KEY_PASSWORD FROM " + DUBBO_HISTORY_TABLE + " LIMIT 1"); } 
                catch (Exception e) { SqliteUtil.update("ALTER TABLE " + DUBBO_HISTORY_TABLE + " ADD COLUMN KEY_PASSWORD VARCHAR;"); }

                try { SqliteUtil.query("SELECT CA_CERT FROM " + DUBBO_HISTORY_TABLE + " LIMIT 1"); } 
                catch (Exception e) { SqliteUtil.update("ALTER TABLE " + DUBBO_HISTORY_TABLE + " ADD COLUMN CA_CERT VARCHAR;"); }

                try { SqliteUtil.query("SELECT PROVIDER_URL FROM " + DUBBO_HISTORY_TABLE + " LIMIT 1"); }
                catch (Exception e) { SqliteUtil.update("ALTER TABLE " + DUBBO_HISTORY_TABLE + " ADD COLUMN PROVIDER_URL VARCHAR;"); }

            }
        } catch (Exception e) {
            logger.error("检查/创建表失败", e);
        }
    }

    /**
     * 查询所有数据，按SORT_ORDER排序
     */
    public List<DubboTreeItem> queryAllData() throws Exception {
        return SqliteUtil.queryForList(
            "SELECT ID, PARENT_ID, NAME, IS_LEAF as isLeaf, ENVNAME, INTERFACEINFO, METHODINFO, IS_SELECTED, REQUEST_TYPE, INPUTTEXT, SORT_ORDER, HOOK_SCRIPT, VERSION, ATTACHMENTS, TIMEOUT as timeout, RETRIES as retries, D_GROUP as \"group\", DUBBO_GROUP as dubboGroup, PROVIDER_URL as providerUrl, TLS_ENABLE as tlsEnable, MUTUAL_AUTH as mutualAuth, CLIENT_CERT as clientCertPath, CLIENT_KEY as clientKeyPath, KEY_PASSWORD as clientKeyPassword, CA_CERT as caCertPath FROM " + DUBBO_TREE_ITEM_TABLE + " ORDER BY SORT_ORDER, ID",   
            DubboTreeItem.class);
    }

    public Long insertData(DubboTreeItem dto) throws Exception {
        BaseDataDto base = dto;
        return SqliteUtil.insert(
            "INSERT INTO " + DUBBO_TREE_ITEM_TABLE + "(PARENT_ID, NAME, IS_LEAF, ENVNAME, INTERFACEINFO, METHODINFO, IS_SELECTED, REQUEST_TYPE, INPUTTEXT, SORT_ORDER, HOOK_SCRIPT, VERSION, ATTACHMENTS, TIMEOUT, RETRIES, D_GROUP, DUBBO_GROUP, PROVIDER_URL, TLS_ENABLE, MUTUAL_AUTH, CLIENT_CERT, CLIENT_KEY, KEY_PASSWORD, CA_CERT) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
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
            dto.getHookScript(),
            dto.getVersion(),
            dto.getAttachments(),
            dto.getTimeout(),
            dto.getRetries(),
            dto.getGroup(),
            dto.getDubboGroup(),
            dto.getProviderUrl(),
            dto.getTlsEnable(),
            dto.getMutualAuth(),
            dto.getClientCertPath(),
            dto.getClientKeyPath(),
            dto.getClientKeyPassword(),
            dto.getCaCertPath());
    }

    public void delLevelData(DubboTreeItem dto) throws Exception {
        SqliteUtil.update("DELETE FROM " + DUBBO_TREE_ITEM_TABLE + " WHERE ID = ?", dto.getId());
    }

    public void updateLevelData(Long newParentId, DubboTreeItem where) throws Exception {
        SqliteUtil.update("UPDATE " + DUBBO_TREE_ITEM_TABLE + " SET PARENT_ID= ? WHERE ID = ?", newParentId, where.getId());
    }

    public void updateData(DubboTreeItem dto) throws Exception {
        BaseDataDto base = dto;
        SqliteUtil.update(
            "UPDATE " + DUBBO_TREE_ITEM_TABLE + " SET NAME=?, PARENT_ID=?, ENVNAME=?, INTERFACEINFO=?, METHODINFO=?, IS_SELECTED=?, REQUEST_TYPE=?, INPUTTEXT=?, SORT_ORDER=?, HOOK_SCRIPT=?, VERSION=?, ATTACHMENTS=?, TIMEOUT=?, RETRIES=?, D_GROUP=?, DUBBO_GROUP=?, PROVIDER_URL=?, TLS_ENABLE=?, MUTUAL_AUTH=?, CLIENT_CERT=?, CLIENT_KEY=?, KEY_PASSWORD=?, CA_CERT=? WHERE ID = ?",
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
            dto.getVersion(),
            dto.getAttachments(),
            dto.getTimeout(),
            dto.getRetries(),
            dto.getGroup(),
            dto.getDubboGroup(),
            dto.getProviderUrl(),
            dto.getTlsEnable(),
            dto.getMutualAuth(),
            dto.getClientCertPath(),
            dto.getClientKeyPath(),
            dto.getClientKeyPassword(),
            dto.getCaCertPath(),
            base.getId());
    }

    public DubboTreeItem queryData(DubboTreeItem dubboTreeItem) throws Exception {
        return SqliteUtil.queryForList(
            "SELECT ENVNAME, INTERFACEINFO, METHODINFO, IS_SELECTED, REQUEST_TYPE, INPUTTEXT, SORT_ORDER, HOOK_SCRIPT, VERSION, ATTACHMENTS, TIMEOUT as timeout, RETRIES as retries, D_GROUP as \"group\", DUBBO_GROUP as dubboGroup, PROVIDER_URL as providerUrl FROM " + DUBBO_TREE_ITEM_TABLE + " WHERE ID = ?",
            DubboTreeItem.class,
            dubboTreeItem.getId()).get(0);
    }

    /**
     * 只更新位置信息（拖拽时使用）
     */
    public void updatePositionOnly(DubboTreeItem dto) throws Exception {
        SqliteUtil.update(
            "UPDATE " + DUBBO_TREE_ITEM_TABLE + " SET PARENT_ID = ?, SORT_ORDER = ? WHERE ID = ?",
            dto.getParentId(),
            dto.getSortOrder() != null ? dto.getSortOrder() : 0,
            dto.getId());
    }

    // ================= DUBBO ENV CONFIG METHODS =================

    public List<com.opencgl.dubbo.model.DubboEnvConfig> queryAllEnvConfig() throws Exception {
        return SqliteUtil.queryForList(
            "SELECT ENV_NAME as envName, REGISTRY_ADDRESS as registryAddress, REGISTRY_GROUP as registryGroup, ZK_GROUP as zkGroup, API_PACKAGE_PATH as apiPackagePath, SERVICE_DATA as serviceData FROM " + DUBBO_ENV_CONFIG_TABLE,
            com.opencgl.dubbo.model.DubboEnvConfig.class);
    }

    public com.opencgl.dubbo.model.DubboEnvConfig queryEnvConfig(String envName) throws Exception {
        List<com.opencgl.dubbo.model.DubboEnvConfig> list = SqliteUtil.queryForList(
            "SELECT ENV_NAME as envName, REGISTRY_ADDRESS as registryAddress, REGISTRY_GROUP as registryGroup, ZK_GROUP as zkGroup, API_PACKAGE_PATH as apiPackagePath, SERVICE_DATA as serviceData FROM " + DUBBO_ENV_CONFIG_TABLE + " WHERE ENV_NAME = ?",
            com.opencgl.dubbo.model.DubboEnvConfig.class,
            envName);
        return list.isEmpty() ? null : list.get(0);
    }

    public void insertEnvConfig(com.opencgl.dubbo.model.DubboEnvConfig config) throws Exception {
        SqliteUtil.insert(
            "INSERT INTO " + DUBBO_ENV_CONFIG_TABLE + "(ENV_NAME, REGISTRY_ADDRESS, REGISTRY_GROUP, ZK_GROUP, API_PACKAGE_PATH, SERVICE_DATA) VALUES (?, ?, ?, ?, ?, ?)",
            config.getEnvName(),
            config.getRegistryAddress(),
            config.getRegistryGroup(),
            config.getZkGroup(),
            config.getApiPackagePath(),
            config.getServiceData());
    }

    public void updateEnvConfig(com.opencgl.dubbo.model.DubboEnvConfig config) throws Exception {
        SqliteUtil.update(
            "UPDATE " + DUBBO_ENV_CONFIG_TABLE + " SET REGISTRY_ADDRESS=?, REGISTRY_GROUP=?, ZK_GROUP=?, API_PACKAGE_PATH=?, SERVICE_DATA=? WHERE ENV_NAME=?",
            config.getRegistryAddress(),
            config.getRegistryGroup(),
            config.getZkGroup(),
            config.getApiPackagePath(),
            config.getServiceData(),
            config.getEnvName());
    }

    public void deleteEnvConfig(String envName) throws Exception {
        SqliteUtil.update("DELETE FROM " + DUBBO_ENV_CONFIG_TABLE + " WHERE ENV_NAME = ?", envName);
    }

    // ================= HISTORY METHODS =================

    public void insertHistory(com.opencgl.dubbo.model.DubboHistoryItem item) throws Exception {
        SqliteUtil.insert(
            "INSERT INTO " + DUBBO_HISTORY_TABLE + "(ID, INTERFACE_NAME, METHOD_NAME, PARAM_TYPE, ADDRESS, TIMESTAMP, STATUS, SUMMARY, VERSION, REQUEST_TYPE, ENV_NAME, INPUT_TEXT, OUTPUT_TEXT, D_GROUP, DUBBO_GROUP, TIMEOUT, RETRIES, HOOK_SCRIPT, ATTACHMENTS, TLS_ENABLE, CLIENT_CERT, CLIENT_KEY, KEY_PASSWORD, CA_CERT) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            item.getId(),
            item.getInterfaceName(),
            item.getMethodName(),
            item.getParamType(),
            item.getAddress(),
            item.getTimestamp().getTime(),
            item.getStatus(),
            item.getSummary(),
            item.getVersion(),
            item.getRequestType(),
            item.getEnvName(),
            item.getInputText(),
            item.getOutputText(),
            item.getGroup(),
            item.getDubboGroup(),
            item.getTimeout(),
            item.getRetries(),
            item.getHookScript(),
            item.getAttachments(),
            item.getTlsEnable(),
            item.getClientCertPath(),
            item.getClientKeyPath(),
            item.getClientKeyPassword(),
            item.getCaCertPath());
    }
    
    public List<com.opencgl.dubbo.model.DubboHistoryItem> queryHistory() throws Exception {
         return SqliteUtil.queryForList(
            "SELECT ID, " +
            "INTERFACE_NAME as interfaceName, " +
            "METHOD_NAME as methodName, " +
            "PARAM_TYPE as paramType, " +
            "ADDRESS as address, " +
            "TIMESTAMP as timestamp, " +
            "STATUS as status, " +
            "SUMMARY as summary, " +
            "VERSION as version, " +
            "REQUEST_TYPE as requestType, " +
            "ENV_NAME as envName, " +
            "INPUT_TEXT as inputText, " +
            "OUTPUT_TEXT as outputText, " +
            "D_GROUP as \"group\", " +
            "DUBBO_GROUP as dubboGroup, " +
            "TIMEOUT as timeout, " +
            "RETRIES as retries, " +
            "HOOK_SCRIPT as hookScript, " +
            "ATTACHMENTS as attachments, " +
            "TLS_ENABLE as tlsEnable, " +
            "CLIENT_CERT as clientCertPath, " +
            "CLIENT_KEY as clientKeyPath, " +
            "KEY_PASSWORD as clientKeyPassword, " +
            "CA_CERT as caCertPath " +
            "FROM " + DUBBO_HISTORY_TABLE + " ORDER BY TIMESTAMP DESC LIMIT 50",
            com.opencgl.dubbo.model.DubboHistoryItem.class);
    }

    public void clearHistory() throws Exception {
        SqliteUtil.update("DELETE FROM " + DUBBO_HISTORY_TABLE);
    }
}
