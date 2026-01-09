package com.opencgl.dubbo.mock.model;

import com.opencgl.base.utils.SqliteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dubbo Mock 树形数据持久化管理器 (SQLite 版)
 * 使用宿主的 SqliteUtil 持久化，数据存储于统一的 data.db 中。
 * 表结构：dubbo_mock_registry / dubbo_mock_provider / dubbo_mock_method
 */
public class DubboMockStoreManager {

    private static final Logger log = LoggerFactory.getLogger(DubboMockStoreManager.class);

    /** 内存中的树形数据（从数据库加载后缓存） */
    private List<RegistryNodeModel> registryNodes = new ArrayList<>();

    public DubboMockStoreManager() {
        initTables();
        loadAll();
    }

    // ==================== 初始化表 ====================

    private void initTables() {
        try {
            if (!SqliteUtil.checkTableExist("dubbo_mock_registry")) {
                SqliteUtil.update(
                    "CREATE TABLE IF NOT EXISTS dubbo_mock_registry (" +
                    "id TEXT PRIMARY KEY, " +
                    "name TEXT, " +
                    "registry_type TEXT DEFAULT 'zookeeper', " +
                    "registry_address TEXT DEFAULT '127.0.0.1:2181', " +
                    "zk_group TEXT DEFAULT '', " +
                    "protocol TEXT DEFAULT 'dubbo', " +
                    "port INTEGER DEFAULT 20880, " +
                    "application_name TEXT DEFAULT 'dubbo-mock-provider', " +
                    "provider_host TEXT DEFAULT '', " +
                    "no_registry INTEGER DEFAULT 0, " +
                    "enabled INTEGER DEFAULT 1, " +
                    "sort_order INTEGER DEFAULT 0" +
                    ")"
                );
                log.info("dubbo_mock_registry 表已创建");
            } else {
                try { SqliteUtil.query("SELECT zk_group FROM dubbo_mock_registry LIMIT 1"); }
                catch (Exception e) { SqliteUtil.update("ALTER TABLE dubbo_mock_registry ADD COLUMN zk_group TEXT DEFAULT ''"); log.info("为表 dubbo_mock_registry 添加 zk_group 字段成功"); }
            }
            if (!SqliteUtil.checkTableExist("dubbo_mock_provider")) {
                SqliteUtil.update(
                    "CREATE TABLE IF NOT EXISTS dubbo_mock_provider (" +
                    "id TEXT PRIMARY KEY, " +
                    "registry_id TEXT NOT NULL, " +
                    "interface_name TEXT DEFAULT '', " +
                    "version TEXT DEFAULT '', " +
                    "group_name TEXT DEFAULT '', " +
                    "sort_order INTEGER DEFAULT 0" +
                    ")"
                );
                log.info("dubbo_mock_provider 表已创建");
            }
            if (!SqliteUtil.checkTableExist("dubbo_mock_method")) {
                SqliteUtil.update(
                    "CREATE TABLE IF NOT EXISTS dubbo_mock_method (" +
                    "id TEXT PRIMARY KEY, " +
                    "provider_id TEXT NOT NULL, " +
                    "method_name TEXT DEFAULT '', " +
                    "response_json TEXT DEFAULT '{}', " +
                    "delay_ms INTEGER DEFAULT 0, " +
                    "enabled INTEGER DEFAULT 1, " +
                    "sort_order INTEGER DEFAULT 0" +
                    ")"
                );
                log.info("dubbo_mock_method 表已创建");
            }
        } catch (Exception e) {
            log.error("初始化 DubboMock 数据库表失败", e);
        }
    }

    // ==================== 全量加载 ====================

    /**
     * 从 SQLite 加载全部数据并组装为内存中的树形结构
     */
    public synchronized void loadAll() {
        try {
            // 1. 加载所有 Registry
            List<Map<String, Object>> regRows = queryMaps("SELECT * FROM dubbo_mock_registry ORDER BY sort_order ASC");
            List<RegistryNodeModel> regs = new ArrayList<>();

            // 2. 加载所有 Provider，按 registry_id 分组
            List<Map<String, Object>> provRows = queryMaps("SELECT * FROM dubbo_mock_provider ORDER BY sort_order ASC");
            Map<String, List<ProviderNodeModel>> provByReg = new HashMap<>();
            for (Map<String, Object> row : provRows) {
                ProviderNodeModel p = mapToProvider(row);
                String regId = str(row.get("registry_id"));
                provByReg.computeIfAbsent(regId, k -> new ArrayList<>()).add(p);
            }

            // 3. 加载所有 Method，按 provider_id 分组
            List<Map<String, Object>> methRows = queryMaps("SELECT * FROM dubbo_mock_method ORDER BY sort_order ASC");
            Map<String, List<MethodMockConfig>> methByProv = new HashMap<>();
            for (Map<String, Object> row : methRows) {
                MethodMockConfig m = mapToMethod(row);
                String provId = str(row.get("provider_id"));
                methByProv.computeIfAbsent(provId, k -> new ArrayList<>()).add(m);
            }

            // 4. 组装树
            for (Map<String, Object> row : regRows) {
                RegistryNodeModel r = mapToRegistry(row);
                List<ProviderNodeModel> providers = provByReg.getOrDefault(r.getId(), new ArrayList<>());
                for (ProviderNodeModel p : providers) {
                    p.setMethods(methByProv.getOrDefault(p.getId(), new ArrayList<>()));
                }
                r.setProviders(providers);
                regs.add(r);
            }

            this.registryNodes = regs;
            log.info("从 SQLite 加载了 {} 个注册中心", registryNodes.size());

            // 如果数据库为空，创建一个默认节点
            if (registryNodes.isEmpty()) {
                RegistryNodeModel def = new RegistryNodeModel();
                def.setName("Local Zookeeper");
                insertRegistry(def);
                registryNodes.add(def);
            }
        } catch (Exception e) {
            log.error("从 SQLite 加载 DubboMock 数据失败", e);
            registryNodes = new ArrayList<>();
        }
    }

    // ==================== Registry CRUD ====================

    public void insertRegistry(RegistryNodeModel r) {
        try {
            int maxOrder = getMaxSortOrder("dubbo_mock_registry");
            SqliteUtil.insert(
                "INSERT INTO dubbo_mock_registry (id, name, registry_type, registry_address, zk_group, protocol, port, application_name, provider_host, no_registry, enabled, sort_order) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                r.getId(), r.getName(), r.getRegistryType(), r.getRegistryAddress(), r.getZkGroup(),
                r.getProtocol(), String.valueOf(r.getPort()), r.getApplicationName(),
                r.getProviderHost(), r.isNoRegistry() ? "1" : "0",
                r.isEnabled() ? "1" : "0", String.valueOf(maxOrder + 1)
            );
        } catch (Exception e) {
            log.error("插入 Registry 失败", e);
        }
    }

    public void updateRegistry(RegistryNodeModel r) {
        try {
            SqliteUtil.update(
                "UPDATE dubbo_mock_registry SET name=?, registry_type=?, registry_address=?, zk_group=?, protocol=?, port=?, application_name=?, provider_host=?, no_registry=?, enabled=? WHERE id=?",
                r.getName(), r.getRegistryType(), r.getRegistryAddress(), r.getZkGroup(),
                r.getProtocol(), String.valueOf(r.getPort()), r.getApplicationName(),
                r.getProviderHost(), r.isNoRegistry() ? "1" : "0",
                r.isEnabled() ? "1" : "0", r.getId()
            );
        } catch (Exception e) {
            log.error("更新 Registry 失败", e);
        }
    }

    public void deleteRegistry(String registryId) {
        try {
            // 级联删除：先删 method，再删 provider，最后删 registry
            SqliteUtil.delete("DELETE FROM dubbo_mock_method WHERE provider_id IN (SELECT id FROM dubbo_mock_provider WHERE registry_id=?)", registryId);
            SqliteUtil.delete("DELETE FROM dubbo_mock_provider WHERE registry_id=?", registryId);
            SqliteUtil.delete("DELETE FROM dubbo_mock_registry WHERE id=?", registryId);
        } catch (Exception e) {
            log.error("删除 Registry 失败", e);
        }
    }

    // ==================== Provider CRUD ====================

    public void insertProvider(String registryId, ProviderNodeModel p) {
        try {
            int maxOrder = getMaxSortOrderFor("dubbo_mock_provider", "registry_id", registryId);
            SqliteUtil.insert(
                "INSERT INTO dubbo_mock_provider (id, registry_id, interface_name, version, group_name, sort_order) VALUES (?,?,?,?,?,?)",
                p.getId(), registryId, p.getInterfaceName(),
                p.getVersion(), p.getGroup(), String.valueOf(maxOrder + 1)
            );
        } catch (Exception e) {
            log.error("插入 Provider 失败", e);
        }
    }

    public void updateProvider(ProviderNodeModel p) {
        try {
            SqliteUtil.update(
                "UPDATE dubbo_mock_provider SET interface_name=?, version=?, group_name=? WHERE id=?",
                p.getInterfaceName(), p.getVersion(), p.getGroup(), p.getId()
            );
        } catch (Exception e) {
            log.error("更新 Provider 失败", e);
        }
    }

    public void deleteProvider(String providerId) {
        try {
            SqliteUtil.delete("DELETE FROM dubbo_mock_method WHERE provider_id=?", providerId);
            SqliteUtil.delete("DELETE FROM dubbo_mock_provider WHERE id=?", providerId);
        } catch (Exception e) {
            log.error("删除 Provider 失败", e);
        }
    }

    /** 将 Provider 移动到另一个 Registry 下 */
    public void moveProvider(String providerId, String newRegistryId) {
        try {
            int maxOrder = getMaxSortOrderFor("dubbo_mock_provider", "registry_id", newRegistryId);
            SqliteUtil.update("UPDATE dubbo_mock_provider SET registry_id=?, sort_order=? WHERE id=?",
                newRegistryId, String.valueOf(maxOrder + 1), providerId);
        } catch (Exception e) {
            log.error("移动 Provider 失败", e);
        }
    }

    // ==================== Method CRUD ====================

    public void insertMethod(String providerId, MethodMockConfig m) {
        try {
            int maxOrder = getMaxSortOrderFor("dubbo_mock_method", "provider_id", providerId);
            SqliteUtil.insert(
                "INSERT INTO dubbo_mock_method (id, provider_id, method_name, response_json, delay_ms, enabled, sort_order) VALUES (?,?,?,?,?,?,?)",
                m.getId(), providerId, m.getMethodName(),
                m.getResponseJson(), String.valueOf(m.getDelayMs()),
                m.isEnabled() ? "1" : "0", String.valueOf(maxOrder + 1)
            );
        } catch (Exception e) {
            log.error("插入 Method 失败", e);
        }
    }

    public void updateMethod(MethodMockConfig m) {
        try {
            SqliteUtil.update(
                "UPDATE dubbo_mock_method SET method_name=?, response_json=?, delay_ms=?, enabled=? WHERE id=?",
                m.getMethodName(), m.getResponseJson(),
                String.valueOf(m.getDelayMs()), m.isEnabled() ? "1" : "0", m.getId()
            );
        } catch (Exception e) {
            log.error("更新 Method 失败", e);
        }
    }

    public void deleteMethod(String methodId) {
        try {
            SqliteUtil.delete("DELETE FROM dubbo_mock_method WHERE id=?", methodId);
        } catch (Exception e) {
            log.error("删除 Method 失败", e);
        }
    }

    /** 将 Method 移动到另一个 Provider 下 */
    public void moveMethod(String methodId, String newProviderId) {
        try {
            int maxOrder = getMaxSortOrderFor("dubbo_mock_method", "provider_id", newProviderId);
            SqliteUtil.update("UPDATE dubbo_mock_method SET provider_id=?, sort_order=? WHERE id=?",
                newProviderId, String.valueOf(maxOrder + 1), methodId);
        } catch (Exception e) {
            log.error("移动 Method 失败", e);
        }
    }

    // ==================== 排序 ====================

    public void updateSortOrders(String table, List<String> orderedIds) {
        try {
            for (int i = 0; i < orderedIds.size(); i++) {
                SqliteUtil.update("UPDATE " + table + " SET sort_order=? WHERE id=?",
                    String.valueOf(i), orderedIds.get(i));
            }
        } catch (Exception e) {
            log.error("更新排序失败", e);
        }
    }

    // ==================== 存取器 ====================

    public List<RegistryNodeModel> getRegistryNodes() {
        return registryNodes;
    }

    public RegistryNodeModel findRegistryById(String id) {
        return registryNodes.stream().filter(r -> r.getId().equals(id)).findFirst().orElse(null);
    }

    /** 旧接口兼容：标记为废弃，内部其实调 loadAll */
    @Deprecated
    public void loadFromDisk() {
        loadAll();
    }

    /** 旧接口兼容：标记为废弃，不再需要全量覆写 */
    @Deprecated
    public void saveToDisk() {
        // no-op: 所有写入操作已改为细粒度方法
        log.debug("saveToDisk() 已被废弃，所有变更通过细粒度方法直接入库");
    }

    // ==================== 内部工具方法 ====================

    private int getMaxSortOrder(String table) {
        try {
            List<Map<String, Object>> rows = queryMaps("SELECT MAX(sort_order) as max_order FROM " + table);
            if (!rows.isEmpty() && rows.get(0).get("max_order") != null) {
                return Integer.parseInt(rows.get(0).get("max_order").toString());
            }
        } catch (Exception e) {
            log.error("获取最大 sort_order 失败", e);
        }
        return 0;
    }

    private int getMaxSortOrderFor(String table, String parentCol, String parentId) {
        try {
            List<Map<String, Object>> rows = queryMaps(
                "SELECT MAX(sort_order) as max_order FROM " + table + " WHERE " + parentCol + "='" + parentId + "'"
            );
            if (!rows.isEmpty() && rows.get(0).get("max_order") != null) {
                return Integer.parseInt(rows.get(0).get("max_order").toString());
            }
        } catch (Exception e) {
            log.error("获取最大 sort_order 失败", e);
        }
        return 0;
    }

    /** 通用查询，返回 List<Map> */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> queryMaps(String sql) throws Exception {
        // SqliteUtil.queryForList 需要 Class 映射，这里用 HashMap 兜底
        return SqliteUtil.queryForList(sql, (Class<Map<String, Object>>)(Class<?>)HashMap.class);
    }

    private String str(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    private int intVal(Object obj) {
        if (obj == null) return 0;
        try { return Integer.parseInt(obj.toString()); }
        catch (NumberFormatException e) { return 0; }
    }

    private boolean boolVal(Object obj) {
        if (obj == null) return false;
        return "1".equals(obj.toString()) || "true".equalsIgnoreCase(obj.toString());
    }

    private RegistryNodeModel mapToRegistry(Map<String, Object> row) {
        RegistryNodeModel r = new RegistryNodeModel();
        r.setId(str(row.get("id")));
        r.setName(str(row.get("name")));
        r.setRegistryType(str(row.get("registry_type")));
        r.setRegistryAddress(str(row.get("registry_address")));
        r.setZkGroup(str(row.get("zk_group")));
        r.setProtocol(str(row.get("protocol")));
        r.setPort(intVal(row.get("port")));
        r.setApplicationName(str(row.get("application_name")));
        r.setProviderHost(str(row.get("provider_host")));
        r.setNoRegistry(boolVal(row.get("no_registry")));
        r.setEnabled(boolVal(row.get("enabled")));
        return r;
    }

    private ProviderNodeModel mapToProvider(Map<String, Object> row) {
        ProviderNodeModel p = new ProviderNodeModel();
        p.setId(str(row.get("id")));
        p.setInterfaceName(str(row.get("interface_name")));
        p.setVersion(str(row.get("version")));
        p.setGroup(str(row.get("group_name")));
        return p;
    }

    private MethodMockConfig mapToMethod(Map<String, Object> row) {
        MethodMockConfig m = new MethodMockConfig();
        m.setId(str(row.get("id")));
        m.setMethodName(str(row.get("method_name")));
        m.setResponseJson(str(row.get("response_json")));
        m.setDelayMs(intVal(row.get("delay_ms")));
        m.setEnabled(boolVal(row.get("enabled")));
        return m;
    }
}
