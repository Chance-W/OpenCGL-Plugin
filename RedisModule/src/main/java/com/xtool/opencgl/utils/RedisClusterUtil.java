package com.xtool.opencgl.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.*;

import java.util.*;

/**
 * @author Chance.W
 * @date 2020/4/14-9:25
 */
public class RedisClusterUtil {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final JedisCluster jedisCluster;

    public RedisClusterUtil(String clusterNode) {
        Set<HostAndPort> clusterNodes = new HashSet<>();
        String[] clusterNodeList = clusterNode.split(",");
        for (String node : clusterNodeList) {
            clusterNodes.add(new HostAndPort(node.split(":")[0], Integer.parseInt(node.split(":")[1])));
        }
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxTotal(50);//最大连接个数
        jedisPoolConfig.setMaxIdle(10);//最大空闲连接个数
        jedisPoolConfig.setMaxWaitMillis(-1);//获取连接时的最大等待毫秒数，若超时则抛异常。-1代表不确定的毫秒数
        jedisPoolConfig.setTestOnBorrow(true);//获取连接时检测其有效性
        this.jedisCluster = new JedisCluster(clusterNodes, 15000, 100, jedisPoolConfig);

    }

    public Boolean queryConnState() {
        return !jedisCluster.getClusterNodes().toString().contains("{}");
    }

    public List<Set<String>> queryAllKey(String keyPa) {
        Map<String, JedisPool> clusterNodes = jedisCluster.getClusterNodes();
        List<Set<String>> keys = new ArrayList<>();
        for (Map.Entry<String, JedisPool> entry : clusterNodes.entrySet()) {
            Jedis jedis = entry.getValue().getResource();
            // 判断非从节点(因为若主从复制，从节点会跟随主节点的变化而变化)
            if (!jedis.info("replication").contains("role:slave")) {
                Set<String> key = jedis.keys(keyPa);

                if (!(null == key)) {
                    keys.add(key);

                }
                /*if (keys.size() > 0) {
                    Map<Integer, List<String>> map = new HashMap<>();
                    for (String key : keys) {
                        // cluster模式执行多key操作的时候，这些key必须在同一个slot上，不然会报:JedisDataException:
                        // CROSSSLOT Keys in request don't hash to the same slot
                        int slot = JedisClusterCRC16.getSlot(key);
                        // 按slot将key分组，相同slot的key一起提交
                        if (map.containsKey(slot)) {
                            map.get(slot).add(key);
                        } else {
                            map.put(slot, Lists.newArrayList(key));
                        }
                    }
                    for (Map.Entry<Integer, List<String>> integerListEntry : map.entrySet()) {
                        System.out.println("integerListEntry=" + integerListEntry);
                        //jedis.del(integerListEntry.getValue().toArray(new String[integerListEntry.getValue().size()]));
                    }
                }*/
            }

        }
        return keys;
    }

    public String queryAppointKey(String key) {
        String result = jedisCluster.get(key);
        if (null == result) {
            return "key值为空";
        }
        else {
            return result;
        }
    }

    public String delAppoinKey(String key) {
        jedisCluster.del(key);
        if (null == jedisCluster.get(key)) {
            return "删除" + key + "成功";
        }
        else {
            return "删除" + key + "失败";
        }
    }

    public void addAppoinKey(String key, String value) {
        jedisCluster.set(key, value);
    }


    public void updateAppoinKey(String key, String value) {
        jedisCluster.set(key, value);
    }

    public Map<String, String> hgetAll(String key) {
        return jedisCluster.hgetAll(key);
    }

    public Long hset(String key, Map<String, String> map) {
        return jedisCluster.hset(key, map);
    }

    public Long hdel(String key, String mapKey) {
        return jedisCluster.hdel(key, mapKey);
    }

    public List<String> mget(String[] keys) {
        return jedisCluster.mget(keys);
    }

    public String mSet(String[] keys) {
        return jedisCluster.mset(keys);
    }


    public void closeJedis() {
        if (jedisCluster != null) {
            jedisCluster.close();
        }
    }


  /*  public static void main(String[] args) {

        Set<HostAndPort> clusterNodes = new HashSet<HostAndPort>();
        clusterNodes.add(new HostAndPort("192.168.0.203", 6379));
        clusterNodes.add(new HostAndPort("192.168.0.204", 6379));
        clusterNodes.add(new HostAndPort("192.168.0.205", 6379));
        clusterNodes.add(new HostAndPort("192.168.0.206", 6379));
        clusterNodes.add(new HostAndPort("192.168.0.207", 6379));
        clusterNodes.add(new HostAndPort("192.168.0.208", 6379));

        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxTotal(50);//最大连接个数
        jedisPoolConfig.setMaxIdle(10);//最大空闲连接个数
        jedisPoolConfig.setMaxWaitMillis(-1);//获取连接时的最大等待毫秒数，若超时则抛异常。-1代表不确定的毫秒数
        jedisPoolConfig.setTestOnBorrow(true);//获取连接时检测其有效性
        JedisCluster jedisCluster = new JedisCluster(clusterNodes, 15000, 100, jedisPoolConfig);//第二个参数：超时时间     第三个参数：最大尝试重连次数

        Map<String, String> map = new HashMap<String, String>();

        jedisCluster.set("id", "1");
        jedisCluster.set("name", "cb");
        jedisCluster.set("age", "10");

        System.out.println(jedisCluster.get("id"));
        System.out.println(jedisCluster.get("name"));
        System.out.println(jedisCluster.get("age"));

        //  jedisCluster.close();//关闭jedisCluster
    }*/

    public static void main(String[] args) {
        RedisClusterUtil redisClusterUtil = new RedisClusterUtil("127.0.0.1:6501");
        for (Set<String> set : redisClusterUtil.queryAllKey("*AAA*")) {
            for (String aaa : set) {
                System.out.println(aaa);
            }
        }

        System.out.println(redisClusterUtil.hgetAll("aaa:bbb"));


        List<String> list = redisClusterUtil.mget(new String[]{"aaaa", "bbbb"});
        for (String res : list) {
            System.out.println(res);
        }

    }
}
