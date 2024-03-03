package com.opencgl.dao;

import java.util.List;

import com.opencgl.base.utils.SqliteUtil;
import com.opencgl.model.RocketMqConsumerWidgetDto;

/**
 * @author Chance.W
 */
public class RocketMqConsumerWidgetDao {

    public void checkTable() throws Exception {
        if (!SqliteUtil.checkTableExist("ROCKET_MQ_CONSUMER_ITEM")) {
            SqliteUtil.update("CREATE TABLE ROCKET_MQ_CONSUMER_ITEM (ID INTEGER PRIMARY KEY AUTOINCREMENT,PARENT_ID INTEGER,NAME VARCHAR(150) NOT NULL,IS_LEAF BOOLEAN NOT NULL,NAMESERVER_ADDR VARCHAR,NAME_TOPIC VARCHAR)");
        }
    }

    public List<RocketMqConsumerWidgetDto> queryAllData() throws Exception {
        return SqliteUtil.queryForList("SELECT * FROM ROCKET_MQ_CONSUMER_ITEM", RocketMqConsumerWidgetDto.class);

    }

    public Long insertData(RocketMqConsumerWidgetDto rockerMqConsumerWidgetDto) throws Exception {
       return SqliteUtil.insert("INSERT INTO ROCKET_MQ_CONSUMER_ITEM(PARENT_ID, NAME, IS_LEAF, NAMESERVER_ADDR ,NAME_TOPIC)VALUES(? ,? ,? ,? ,?)",
            rockerMqConsumerWidgetDto.getParentId(),
            rockerMqConsumerWidgetDto.getName(),
            rockerMqConsumerWidgetDto.getIsLeaf(),
            rockerMqConsumerWidgetDto.getNameServerAddr(),
            rockerMqConsumerWidgetDto.getNameTopic());
    }

    public void delLevelData(RocketMqConsumerWidgetDto rockerMqConsumerWidgetDto) throws Exception {
        SqliteUtil.update("DELETE FROM ROCKET_MQ_CONSUMER_ITEM WHERE ID = ?", rockerMqConsumerWidgetDto.getId());
    }

    public void updateData(RocketMqConsumerWidgetDto rockerMqConsumerWidgetDto) throws Exception {
        SqliteUtil.update("UPDATE ROCKET_MQ_CONSUMER_ITEM SET SET NAME=?, NAMESERVER_ADDR = ? ,NAME_TOPIC =? WHERE ID = ?",
            rockerMqConsumerWidgetDto.getName(),
            rockerMqConsumerWidgetDto.getNameServerAddr(),
            rockerMqConsumerWidgetDto.getNameTopic(),
            rockerMqConsumerWidgetDto.getId());
    }
}

