package com.opencgl.redis.service;

import com.opencgl.redis.i18n.I18N;
import com.opencgl.redis.model.RedisKeyInfo;
import com.opencgl.redis.model.NewRedisKey;
import com.opencgl.redis.model.RedisWidgetDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.*;

import java.util.*;

/**
 * Redis 连接管理器
 * 支持单机/集群/哨兵三种模式
 */
public class RedisConnectionManager {

    // Single-key script: duplicate detection and creation cannot interleave with another client.
    private static final String CREATE_KEY_SCRIPT = """
        if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
        local args = {ARGV[1], KEYS[1]}
        for i = 3, #ARGV do args[#args + 1] = ARGV[i] end
        redis.call(unpack(args))
        if tonumber(ARGV[2]) > 0 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
        return 1
        """;

    public synchronized boolean createKey(NewRedisKey request) {
        if (!connected) throw new IllegalStateException(I18N.get("newkey.disconnected"));
        return Long.valueOf(1).equals(evalKey(CREATE_KEY_SCRIPT, request.key(), request.arguments()));
    }

    private Object evalKey(String script, String key, List<String> args) {
        if (jedis != null) return jedis.eval(script, List.of(key), args);
        if (jedisCluster != null) return jedisCluster.eval(script, List.of(key), args);
        if (sentinelPool != null) {
            try (Jedis j = sentinelPool.getResource()) { return j.eval(script, List.of(key), args); }
        }
        throw new IllegalStateException(I18N.get("newkey.disconnected"));
    }

    private Map<String, Map<String, String>> getStream(String key) {
        Object reply = evalKey("return redis.call('XRANGE', KEYS[1], '-', '+', 'COUNT', 200)", key, List.of());
        Map<String, Map<String, String>> entries = new LinkedHashMap<>();
        for (Object item : (List<?>) reply) {
            List<?> entry = (List<?>) item;
            List<?> fields = (List<?>) entry.get(1);
            Map<String, String> values = new LinkedHashMap<>();
            for (int i = 0; i < fields.size(); i += 2) values.put(redisText(fields.get(i)), redisText(fields.get(i + 1)));
            entries.put(redisText(entry.get(0)), values);
        }
        return entries;
    }

    private static String redisText(Object value) {
        return value instanceof byte[] bytes ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8) : String.valueOf(value);
    }

    private static final Logger logger = LoggerFactory.getLogger(RedisConnectionManager.class);

    private RedisWidgetDto config;
    private Jedis jedis;
    private JedisCluster jedisCluster;
    private JedisSentinelPool sentinelPool;
    private volatile boolean connected = false;

    public RedisConnectionManager() {
    }

    public RedisConnectionManager(RedisWidgetDto config) {
        this.config = config;
    }

    /**
     * 连接 Redis
     */
    public synchronized boolean connect() {
        if (config == null) {
            logger.error("Config is null");
            return false;
        }

        try {
            disconnect(); // 先断开旧连接

            switch (config.getConnectionType()) {
                case STANDALONE:
                    return connectStandalone();
                case CLUSTER:
                    return connectCluster();
                case SENTINEL:
                    return connectSentinel();
                default:
                    return false;
            }
        } catch (Exception e) {
            logger.error("Failed to connect Redis", e);
            return false;
        }
    }

    private boolean connectStandalone() {
        jedis = new Jedis(config.getHost(), config.getPort());

        if (config.getPassword() != null && !config.getPassword().isEmpty()) {
            jedis.auth(config.getPassword());
        }

        if (config.getDatabase() != null && config.getDatabase() > 0) {
            jedis.select(config.getDatabase());
        }

        String pong = jedis.ping();
        connected = "PONG".equalsIgnoreCase(pong);
        return connected;
    }

    private boolean connectCluster() {
        Set<HostAndPort> nodes = parseNodes(config.getClusterNodes());
        if (nodes.isEmpty()) {
            return false;
        }

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);

        if (config.getPassword() != null && !config.getPassword().isEmpty()) {
            jedisCluster = new JedisCluster(nodes, 2000, 2000, 5, config.getPassword(), poolConfig);
        } else {
            jedisCluster = new JedisCluster(nodes, poolConfig);
        }

        // 测试连接
        jedisCluster.get("__test_connection__");
        connected = true;
        return true;
    }

    private boolean connectSentinel() {
        Set<String> sentinels = new HashSet<>(Arrays.asList(config.getSentinelNodes().split(",")));

        if (config.getPassword() != null && !config.getPassword().isEmpty()) {
            sentinelPool = new JedisSentinelPool(config.getSentinelMaster(), sentinels,
                    new JedisPoolConfig(), 2000, config.getPassword());
        } else {
            sentinelPool = new JedisSentinelPool(config.getSentinelMaster(), sentinels);
        }

        try (Jedis j = sentinelPool.getResource()) {
            String pong = j.ping();
            connected = "PONG".equalsIgnoreCase(pong);
        }
        return connected;
    }

    /**
     * 断开连接
     */
    public synchronized void disconnect() {
        try {
            if (jedis != null) {
                jedis.close();
                jedis = null;
            }
            if (jedisCluster != null) {
                jedisCluster.close();
                jedisCluster = null;
            }
            if (sentinelPool != null) {
                sentinelPool.close();
                sentinelPool = null;
            }
            connected = false;
        } catch (Exception e) {
            logger.error("Failed to disconnect", e);
        }
    }

    /**
     * 执行命令
     */
    public synchronized String executeCommand(String command) {
        if (!connected) {
            return I18N.get("message.error.not_connected");
        }

        try {
            String[] parts = command.trim().split("\\s+");
            if (parts.length == 0) {
                return "";
            }

            String cmd = parts[0].toUpperCase();
            String[] args = Arrays.copyOfRange(parts, 1, parts.length);

            Object result = executeRawCommand(cmd, args);
            return formatResult(result);

        } catch (Exception e) {
            return I18N.get("message.error.generic", e.getMessage());
        }
    }

    private Object executeRawCommand(String cmd, String... args) {
        // 使用具体命令方法替代 sendCommand
        if (jedis != null) {
            return executeJedisCommand(jedis, cmd, args);
        } else if (jedisCluster != null) {
            return executeClusterCommand(cmd, args);
        } else if (sentinelPool != null) {
            try (Jedis j = sentinelPool.getResource()) {
                return executeJedisCommand(j, cmd, args);
            }
        }
        return null;
    }

    private Object executeJedisCommand(Jedis j, String cmd, String... args) {
        switch (cmd) {
            case "GET":
                return args.length > 0 ? j.get(args[0]) : null;
            case "SET":
                return args.length > 1 ? j.set(args[0], args[1]) : null;
            case "DEL":
                return args.length > 0 ? j.del(args[0]) : null;
            case "KEYS":
                return args.length > 0 ? j.keys(args[0]) : null;
            case "TYPE":
                return args.length > 0 ? j.type(args[0]) : null;
            case "TTL":
                return args.length > 0 ? j.ttl(args[0]) : null;
            case "EXPIRE":
                return args.length > 1 ? j.expire(args[0], Integer.parseInt(args[1])) : null;
            case "HGET":
                return args.length > 1 ? j.hget(args[0], args[1]) : null;
            case "HGETALL":
                return args.length > 0 ? j.hgetAll(args[0]) : null;
            case "HSET":
                return args.length > 2 ? j.hset(args[0], args[1], args[2]) : null;
            case "INFO":
                return j.info();
            case "PING":
                return j.ping();
            case "DBSIZE":
                return j.dbSize();
            case "FLUSHDB":
                return j.flushDB();
            case "SELECT":
                return args.length > 0 ? j.select(Integer.parseInt(args[0])) : null;
            default:
                return I18N.get("message.error.unsupported_command", cmd);
        }
    }

    private Object executeClusterCommand(String cmd, String... args) {
        // 简化实现 - 常用命令
        switch (cmd) {
            case "GET":
                return args.length > 0 ? jedisCluster.get(args[0]) : null;
            case "SET":
                return args.length > 1 ? jedisCluster.set(args[0], args[1]) : null;
            case "DEL":
                return args.length > 0 ? jedisCluster.del(args[0]) : null;
            case "KEYS":
                return args.length > 0 ? getKeys(args[0]) : null;
            case "TYPE":
                return args.length > 0 ? jedisCluster.type(args[0]) : null;
            case "TTL":
                return args.length > 0 ? jedisCluster.ttl(args[0]) : null;
            case "EXPIRE":
                return args.length > 1 ? jedisCluster.expire(args[0], Integer.parseInt(args[1])) : null;
            case "HGET":
                return args.length > 1 ? jedisCluster.hget(args[0], args[1]) : null;
            case "HGETALL":
                return args.length > 0 ? jedisCluster.hgetAll(args[0]) : null;
            case "HSET":
                return args.length > 2 ? jedisCluster.hset(args[0], args[1], args[2]) : null;
            case "PING":
                return "PONG";
            default:
                return I18N.get("message.error.unsupported_cluster_command", cmd);
        }
    }

    /**
     * 扫描结果封装 - 包含 keys 和游标状态，用于懒加载分页
     */
    public static class ScanKeysResult {
        private final Set<String> keys;
        private final String cursor;        // 当前游标位置
        private final boolean hasMore;      // 是否还有更多数据
        private final int clusterNodeIndex; // 集群模式下当前扫描到第几个节点

        public ScanKeysResult(Set<String> keys, String cursor, boolean hasMore, int clusterNodeIndex) {
            this.keys = keys;
            this.cursor = cursor;
            this.hasMore = hasMore;
            this.clusterNodeIndex = clusterNodeIndex;
        }

        public Set<String> getKeys() { return keys; }
        public String getCursor() { return cursor; }
        public boolean hasMore() { return hasMore; }
        public int getClusterNodeIndex() { return clusterNodeIndex; }
    }

    /** 每页加载的 key 数量 */
    private static final int PAGE_SIZE = 200;

    /**
     * 分页扫描 Keys（懒加载入口）
     * @param pattern   匹配模式
     * @param prevCursor 上次游标，首次传 null
     * @param prevClusterNodeIndex 上次集群节点索引，首次传 0
     * @return 包含本页 keys 和用于下次加载的游标信息
     */
    public synchronized ScanKeysResult scanKeys(String pattern, String prevCursor, int prevClusterNodeIndex) {
        Set<String> keys = new TreeSet<>();
        ScanParams scanParams = new ScanParams().match(pattern).count(PAGE_SIZE);

        try {
            if (jedis != null) {
                String cursor = (prevCursor == null) ? ScanParams.SCAN_POINTER_START : prevCursor;
                do {
                    ScanResult<String> result = jedis.scan(cursor, scanParams);
                    keys.addAll(result.getResult());
                    cursor = result.getCursor();
                    if (keys.size() >= PAGE_SIZE) {
                        // 本页够了，返回游标供下次继续
                        boolean done = cursor.equals(ScanParams.SCAN_POINTER_START);
                        return new ScanKeysResult(keys, cursor, !done, 0);
                    }
                } while (!cursor.equals(ScanParams.SCAN_POINTER_START));
                return new ScanKeysResult(keys, ScanParams.SCAN_POINTER_START, false, 0);

            } else if (jedisCluster != null) {
                List<JedisPool> pools = new ArrayList<>(jedisCluster.getClusterNodes().values());
                String cursor = (prevCursor == null) ? ScanParams.SCAN_POINTER_START : prevCursor;
                int nodeIdx = prevClusterNodeIndex;

                while (nodeIdx < pools.size() && keys.size() < PAGE_SIZE) {
                    try (Jedis j = pools.get(nodeIdx).getResource()) {
                        do {
                            ScanResult<String> result = j.scan(cursor, scanParams);
                            keys.addAll(result.getResult());
                            cursor = result.getCursor();
                            if (keys.size() >= PAGE_SIZE) {
                                boolean nodeDone = cursor.equals(ScanParams.SCAN_POINTER_START);
                                if (nodeDone) {
                                    nodeIdx++;
                                    cursor = ScanParams.SCAN_POINTER_START;
                                }
                                boolean hasMore = !nodeDone || nodeIdx < pools.size();
                                return new ScanKeysResult(keys, cursor, hasMore, nodeIdx);
                            }
                        } while (!cursor.equals(ScanParams.SCAN_POINTER_START));
                    } catch (Exception e) {
                        // 忽略异常节点
                    }
                    nodeIdx++;
                    cursor = ScanParams.SCAN_POINTER_START;
                }
                return new ScanKeysResult(keys, ScanParams.SCAN_POINTER_START, false, nodeIdx);

            } else if (sentinelPool != null) {
                try (Jedis j = sentinelPool.getResource()) {
                    String cursor = (prevCursor == null) ? ScanParams.SCAN_POINTER_START : prevCursor;
                    do {
                        ScanResult<String> result = j.scan(cursor, scanParams);
                        keys.addAll(result.getResult());
                        cursor = result.getCursor();
                        if (keys.size() >= PAGE_SIZE) {
                            boolean done = cursor.equals(ScanParams.SCAN_POINTER_START);
                            return new ScanKeysResult(keys, cursor, !done, 0);
                        }
                    } while (!cursor.equals(ScanParams.SCAN_POINTER_START));
                    return new ScanKeysResult(keys, ScanParams.SCAN_POINTER_START, false, 0);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to scan keys", e);
        }
        return new ScanKeysResult(keys, ScanParams.SCAN_POINTER_START, false, 0);
    }

    /**
     * 获取所有 Keys（兼容旧接口，内部使用 SCAN）
     */
    public Set<String> getKeys(String pattern) {
        Set<String> allKeys = new TreeSet<>();
        String cursor = null;
        int nodeIdx = 0;
        while (true) {
            ScanKeysResult result = scanKeys(pattern, cursor, nodeIdx);
            allKeys.addAll(result.getKeys());
            if (!result.hasMore() || allKeys.size() >= 5000) break;
            cursor = result.getCursor();
            nodeIdx = result.getClusterNodeIndex();
        }
        return allKeys;
    }

    /**
     * 获取 Key 信息
     */
    public synchronized RedisKeyInfo getKeyInfo(String key) {
        try {
            String type;
            long ttl;

            if (jedis != null) {
                type = jedis.type(key);
                ttl = jedis.ttl(key);
            } else if (jedisCluster != null) {
                type = jedisCluster.type(key);
                ttl = jedisCluster.ttl(key);
            } else if (sentinelPool != null) {
                try (Jedis j = sentinelPool.getResource()) {
                    type = j.type(key);
                    ttl = j.ttl(key);
                }
            } else {
                return null;
            }

            RedisKeyInfo info = new RedisKeyInfo();
            info.setKey(key);
            info.setType(RedisKeyInfo.parseType(type));
            info.setTtl(ttl);
            return info;

        } catch (Exception e) {
            logger.error("Failed to get key info", e);
            return null;
        }
    }

    /**
     * 获取值
     */
    public synchronized Object getValue(String key) {
        if (!connected)
            return null;

        try {
            RedisKeyInfo info = getKeyInfo(key);
            if (info == null)
                return null;

            switch (info.getType()) {
                case STRING:
                    return getString(key);
                case HASH:
                    return getHash(key);
                case LIST:
                    return getList(key);
                case SET:
                    return getSet(key);
                case ZSET:
                    return getZSet(key);
                case STREAM:
                    return getStream(key);
                default:
                    return null;
            }
        } catch (Exception e) {
            logger.error("Failed to get value", e);
            return null;
        }
    }

    private String getString(String key) {
        if (jedis != null)
            return jedis.get(key);
        if (jedisCluster != null)
            return jedisCluster.get(key);
        if (sentinelPool != null) {
            try (Jedis j = sentinelPool.getResource()) {
                return j.get(key);
            }
        }
        return null;
    }

    private Map<String, String> getHash(String key) {
        if (jedis != null)
            return jedis.hgetAll(key);
        if (jedisCluster != null)
            return jedisCluster.hgetAll(key);
        if (sentinelPool != null) {
            try (Jedis j = sentinelPool.getResource()) {
                return j.hgetAll(key);
            }
        }
        return null;
    }

    private List<String> getList(String key) {
        if (jedis != null)
            return jedis.lrange(key, 0, -1);
        if (jedisCluster != null)
            return jedisCluster.lrange(key, 0, -1);
        if (sentinelPool != null) {
            try (Jedis j = sentinelPool.getResource()) {
                return j.lrange(key, 0, -1);
            }
        }
        return null;
    }

    private Set<String> getSet(String key) {
        if (jedis != null)
            return jedis.smembers(key);
        if (jedisCluster != null)
            return jedisCluster.smembers(key);
        if (sentinelPool != null) {
            try (Jedis j = sentinelPool.getResource()) {
                return j.smembers(key);
            }
        }
        return null;
    }

    private List<Tuple> getZSet(String key) {
        if (jedis != null)
            return new ArrayList<>(jedis.zrangeWithScores(key, 0, -1));
        if (jedisCluster != null)
            return new ArrayList<>(jedisCluster.zrangeWithScores(key, 0, -1));
        if (sentinelPool != null) {
            try (Jedis j = sentinelPool.getResource()) {
                return new ArrayList<>(j.zrangeWithScores(key, 0, -1));
            }
        }
        return null;
    }

    /**
     * 设置 String 类型的值
     */
    public synchronized boolean setStringValue(String key, String value) {
        try {
            if (jedis != null) {
                jedis.set(key, value);
            } else if (jedisCluster != null) {
                jedisCluster.set(key, value);
            } else if (sentinelPool != null) {
                try (Jedis j = sentinelPool.getResource()) {
                    j.set(key, value);
                }
            } else {
                return false;
            }
            return true;
        } catch (Exception e) {
            logger.error("Failed to set value", e);
            return false;
        }
    }

    /**
     * 删除 Key
     */
    public synchronized boolean deleteKey(String key) {
        try {
            long result;
            if (jedis != null) {
                result = jedis.del(key);
            } else if (jedisCluster != null) {
                result = jedisCluster.del(key);
            } else if (sentinelPool != null) {
                try (Jedis j = sentinelPool.getResource()) {
                    result = j.del(key);
                }
            } else {
                return false;
            }
            return result > 0;
        } catch (Exception e) {
            logger.error("Failed to delete key", e);
            return false;
        }
    }

    /**
     * 获取服务器信息
     */
    public synchronized String getServerInfo() {
        try {
            if (jedis != null) {
                return jedis.info();
            } else if (sentinelPool != null) {
                try (Jedis j = sentinelPool.getResource()) {
                    return j.info();
                }
            } else if (jedisCluster != null) {
                StringBuilder sb = new StringBuilder();
                Map<String, JedisPool> nodes = jedisCluster.getClusterNodes();
                for (Map.Entry<String, JedisPool> entry : nodes.entrySet()) {
                    sb.append("=== Node: ").append(entry.getKey()).append(" ===\n");
                    try (Jedis j = entry.getValue().getResource()) {
                        sb.append(j.info()).append("\n");
                    }
                }
                return sb.toString();
            }
        } catch (Exception e) {
            return I18N.get("message.error.generic", e.getMessage());
        }
        return "";
    }

    private Set<HostAndPort> parseNodes(String nodesStr) {
        Set<HostAndPort> nodes = new HashSet<>();
        if (nodesStr == null || nodesStr.isEmpty()) {
            return nodes;
        }

        for (String node : nodesStr.split(",")) {
            String[] parts = node.trim().split(":");
            if (parts.length == 2) {
                nodes.add(new HostAndPort(parts[0], Integer.parseInt(parts[1])));
            }
        }
        return nodes;
    }

    private String formatResult(Object result) {
        if (result == null) {
            return "(nil)";
        }
        if (result instanceof byte[]) {
            return new String((byte[]) result);
        }
        if (result instanceof Collection) {
            StringBuilder sb = new StringBuilder();
            int i = 1;
            for (Object item : (Collection<?>) result) {
                sb.append(i++).append(") ").append(formatResult(item)).append("\n");
            }
            return sb.toString().trim();
        }
        return result.toString();
    }

    // Getters
    public boolean isConnected() {
        return connected;
    }

    public RedisWidgetDto getConfig() {
        return config;
    }

    public void setConfig(RedisWidgetDto config) {
        this.config = config;
    }
}
