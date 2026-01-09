package com.opencgl.redis.dao;

import java.util.List;

import com.opencgl.base.utils.SqliteUtil;
import com.opencgl.redis.model.RedisConnectionType;
import com.opencgl.redis.model.RedisWidgetDto;

/**
 * Redis 连接配置 DAO
 * @author Chance.W
 */
public class RedisWidgetDao {

    private static final String TABLE_NAME = "REDIS_CONNECTION";

    public void checkTable() throws Exception {
        if (!SqliteUtil.checkTableExist(TABLE_NAME)) {
            SqliteUtil.update("CREATE TABLE " + TABLE_NAME + " (" +
                "ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                "PARENT_ID INTEGER," +
                "NAME VARCHAR(150) NOT NULL," +
                "IS_LEAF BOOLEAN NOT NULL," +
                "CONNECTION_NAME VARCHAR(150)," +
                "CONNECTION_TYPE VARCHAR(20) DEFAULT 'STANDALONE'," +
                "HOST VARCHAR(100) DEFAULT 'localhost'," +
                "PORT INTEGER DEFAULT 6379," +
                "PASSWORD VARCHAR(100)," +
                "DATABASE_NUM INTEGER DEFAULT 0," +
                "CLUSTER_NODES TEXT," +
                "SENTINEL_MASTER VARCHAR(100)," +
                "SENTINEL_NODES TEXT" +
            ")");
        }
    }

    public List<RedisWidgetDto> queryAll() throws Exception {
        List<RedisWidgetDto> list = SqliteUtil.queryForList(
            "SELECT * FROM " + TABLE_NAME, RedisWidgetDto.class);
        // 手动设置连接类型（因为枚举字段需要转换）
        if (list != null) {
            for (RedisWidgetDto dto : list) {
                if (dto.getConnectionType() == null) {
                    dto.setConnectionType(RedisConnectionType.STANDALONE);
                }
            }
        }
        return list;
    }

    public Long insert(RedisWidgetDto dto) throws Exception {
        dto.setIsLeaf(true);
        String connType = dto.getConnectionType() != null 
            ? dto.getConnectionType().name() : "STANDALONE";
        
        return SqliteUtil.insert(
            "INSERT INTO " + TABLE_NAME + 
            "(PARENT_ID, NAME, IS_LEAF, CONNECTION_NAME, CONNECTION_TYPE, HOST, PORT, PASSWORD, DATABASE_NUM, CLUSTER_NODES, SENTINEL_MASTER, SENTINEL_NODES) " +
            "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            dto.getParentId(),
            dto.getName() != null ? dto.getName() : dto.getConnectionName(),
            dto.getIsLeaf(),
            dto.getConnectionName(),
            connType,
            dto.getHost(),
            dto.getPort(),
            dto.getPassword(),
            dto.getDatabase(),
            dto.getClusterNodes(),
            dto.getSentinelMaster(),
            dto.getSentinelNodes()
        );
    }

    public void delete(Long id) throws Exception {
        SqliteUtil.update("DELETE FROM " + TABLE_NAME + " WHERE ID = ?", id);
    }

    public void update(RedisWidgetDto dto) throws Exception {
        String connType = dto.getConnectionType() != null 
            ? dto.getConnectionType().name() : "STANDALONE";
            
        SqliteUtil.update(
            "UPDATE " + TABLE_NAME + " SET " +
            "CONNECTION_NAME=?, CONNECTION_TYPE=?, HOST=?, PORT=?, PASSWORD=?, " +
            "DATABASE_NUM=?, CLUSTER_NODES=?, SENTINEL_MASTER=?, SENTINEL_NODES=?, NAME=? " +
            "WHERE ID=?",
            dto.getConnectionName(),
            connType,
            dto.getHost(),
            dto.getPort(),
            dto.getPassword(),
            dto.getDatabase(),
            dto.getClusterNodes(),
            dto.getSentinelMaster(),
            dto.getSentinelNodes(),
            dto.getName() != null ? dto.getName() : dto.getConnectionName(),
            dto.getId()
        );
    }
}
