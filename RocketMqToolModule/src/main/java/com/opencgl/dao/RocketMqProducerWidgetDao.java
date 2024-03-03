package com.opencgl.dao;

import java.util.List;

import com.opencgl.base.utils.SqliteUtil;
import com.opencgl.model.RocketMqProducerWidgetDto;

/**
 * @author Chance.W
 */
public class RocketMqProducerWidgetDao {

    public void checkTable() throws Exception {
        if (!SqliteUtil.checkTableExist("ROCKET_MQ_PRODUCER_ITEM")) {
            SqliteUtil.update("CREATE TABLE ROCKET_MQ_PRODUCER_ITEM (ID INTEGER PRIMARY KEY AUTOINCREMENT,PARENT_ID INTEGER,NAME VARCHAR(150) NOT NULL,IS_LEAF BOOLEAN NOT NULL,NAMESERVER_ADDR VARCHAR,NAME_TOPIC VARCHAR,TAGS VARCHAR,COUNT VARCHAR,INPUT_TEXT VARCHAR)");
        }
    }

    public List<RocketMqProducerWidgetDto> queryAllData() throws Exception {
        return SqliteUtil.queryForList("SELECT * FROM ROCKET_MQ_PRODUCER_ITEM", RocketMqProducerWidgetDto.class);

    }

    public Long insertData(RocketMqProducerWidgetDto rockerMqProducerWidgetDto) throws Exception {
       return SqliteUtil.insert("INSERT INTO ROCKET_MQ_PRODUCER_ITEM(PARENT_ID, NAME, IS_LEAF, NAMESERVER_ADDR ,NAME_TOPIC ,TAGS ,COUNT ,INPUT_TEXT)VALUES(? ,? ,? ,? ,? ,? ,? ,?)",
            rockerMqProducerWidgetDto.getParentId(),
            rockerMqProducerWidgetDto.getName(),
            rockerMqProducerWidgetDto.getIsLeaf(),
            rockerMqProducerWidgetDto.getNameServerAddr(),
            rockerMqProducerWidgetDto.getNameTopic(),
            rockerMqProducerWidgetDto.getTags(),
            rockerMqProducerWidgetDto.getCount(),
            rockerMqProducerWidgetDto.getInputText());
    }

    public void delLevelData(RocketMqProducerWidgetDto rockerMqProducerWidgetDto) throws Exception {
        SqliteUtil.update("DELETE FROM ROCKET_MQ_PRODUCER_ITEM WHERE ID = ?",
            rockerMqProducerWidgetDto.getId());
    }

    public void updateData(RocketMqProducerWidgetDto rockerMqProducerWidgetDto) throws Exception {
        SqliteUtil.update("UPDATE ROCKET_MQ_PRODUCER_ITEM SET NAME=?, NAMESERVER_ADDR=?, NAME_TOPIC=? ,TAGS=? ,COUNT=? ,INPUT_TEXT=? WHERE ID = ?",
            rockerMqProducerWidgetDto.getName(),
            rockerMqProducerWidgetDto.getNameServerAddr(),
            rockerMqProducerWidgetDto.getNameTopic(),
            rockerMqProducerWidgetDto.getTags(),
            rockerMqProducerWidgetDto.getCount(),
            rockerMqProducerWidgetDto.getInputText(),
            rockerMqProducerWidgetDto.getId());
    }
}

