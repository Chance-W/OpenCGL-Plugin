package com.xtool.opencgl.dao;

import java.util.List;

import com.opencgl.base.utils.SqliteUtil;
import com.xtool.opencgl.model.RedisWidgetDto;

/**
 * @author Chance.W
 */
public class RedisWidgetDao {

    public void checkTable() throws Exception {
        if (!SqliteUtil.checkTableExist("REDIS_ITEM")) {
            SqliteUtil.update("CREATE TABLE REDIS_ITEM (ID INTEGER PRIMARY KEY AUTOINCREMENT,PARENT_ID INTEGER,NAME VARCHAR(150) NOT NULL,IS_LEAF BOOLEAN NOT NULL,REDIS_IPADDRESS VARCHAR)");
        }
    }

    public List<RedisWidgetDto> queryAllData() throws Exception {
        return SqliteUtil.queryForList("SELECT * FROM REDIS_ITEM", RedisWidgetDto.class);
    }

    public Long insertData(RedisWidgetDto redisWidgetDto) throws Exception {
        return SqliteUtil.insert("INSERT INTO REST_ITEM(PARENT_ID, NAME, IS_LEAF, REDIS_IPADDRESS)VALUES(? ,? ,? ,?)",
            redisWidgetDto.getParentId(),
            redisWidgetDto.getName(),
            redisWidgetDto.getIsLeaf(),
            redisWidgetDto.getRedisIpAddress());
    }

    public void delLevelData(RedisWidgetDto redisWidgetDto) throws Exception {
        SqliteUtil.update("DELETE FROM REDIS_ITEM WHERE ID = ?",
            redisWidgetDto.getId());
    }

    public void updateData(RedisWidgetDto restWidgetDto) throws Exception {
        SqliteUtil.update("UPDATE REDIS_ITEM SET REDIS_IPADDRESS=? WHERE ID = ?",
            restWidgetDto.getRedisIpAddress(),
            restWidgetDto.getId());
    }
}

