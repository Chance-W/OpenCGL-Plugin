package com.opencgl.dao;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencgl.base.model.BaseDataDto;
import com.opencgl.base.utils.SqliteUtil;
import com.opencgl.model.RocketMqConsumerWidgetDto;

/**
 * @author Chance.W
 * 
 * 增加 SORT_ORDER 字段支持拖拽排序
 */
public class RocketMqConsumerWidgetDao {

    private static final Logger logger = LoggerFactory.getLogger(RocketMqConsumerWidgetDao.class);
    private static final String TABLE_NAME = "ROCKET_MQ_CONSUMER_ITEM";

    public void checkTable() throws Exception {
        if (!SqliteUtil.checkTableExist(TABLE_NAME)) {
            // 创建表，包含SORT_ORDER字段
            SqliteUtil.update("CREATE TABLE " + TABLE_NAME + " (" +
                "ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                "PARENT_ID INTEGER," +
                "NAME VARCHAR(150) NOT NULL," +
                "IS_LEAF BOOLEAN NOT NULL," +
                "NAMESERVER_ADDR VARCHAR," +
                "NAME_TOPIC VARCHAR," +
                "SORT_ORDER INTEGER DEFAULT 0" +
                ")");
            logger.info("创建表 {} 成功", TABLE_NAME);
        } else {
            // 检查是否有SORT_ORDER字段，没有则添加
            try {
                SqliteUtil.query("SELECT SORT_ORDER FROM " + TABLE_NAME + " LIMIT 1");
            } catch (Exception e) {
                SqliteUtil.update("ALTER TABLE " + TABLE_NAME + " ADD COLUMN SORT_ORDER INTEGER DEFAULT 0;");
                logger.info("为表 {} 添加 SORT_ORDER 字段成功", TABLE_NAME);
            }
        }
    }

    /**
     * 查询所有数据，按SORT_ORDER排序
     */
    public List<RocketMqConsumerWidgetDto> queryAllData() throws Exception {
        return SqliteUtil.queryForList(
            "SELECT ID, PARENT_ID, NAME, IS_LEAF, NAMESERVER_ADDR, NAME_TOPIC, SORT_ORDER FROM " + TABLE_NAME + " ORDER BY SORT_ORDER, ID", 
            RocketMqConsumerWidgetDto.class);
    }

    public Long insertData(RocketMqConsumerWidgetDto dto) throws Exception {
        BaseDataDto base = dto;
        return SqliteUtil.insert(
            "INSERT INTO " + TABLE_NAME + "(PARENT_ID, NAME, IS_LEAF, NAMESERVER_ADDR, NAME_TOPIC, SORT_ORDER) VALUES(?, ?, ?, ?, ?, ?)",
            base.getParentId(),
            base.getName(),
            base.getIsLeaf(),
            dto.getNameServerAddr(),
            dto.getNameTopic(),
            base.getSortOrder() != null ? base.getSortOrder() : 0);
    }

    public void delLevelData(RocketMqConsumerWidgetDto dto) throws Exception {
        SqliteUtil.update("DELETE FROM " + TABLE_NAME + " WHERE ID = ?", dto.getId());
    }

    public void updateData(RocketMqConsumerWidgetDto dto) throws Exception {
        BaseDataDto base = dto;
        SqliteUtil.update(
            "UPDATE " + TABLE_NAME + " SET NAME=?, PARENT_ID=?, NAMESERVER_ADDR=?, NAME_TOPIC=?, SORT_ORDER=? WHERE ID = ?",
            base.getName(),
            base.getParentId(),
            dto.getNameServerAddr(),
            dto.getNameTopic(),
            base.getSortOrder() != null ? base.getSortOrder() : 0,
            base.getId());
    }

    /**
     * 只更新位置信息（拖拽时使用）
     */
    public void updatePositionOnly(RocketMqConsumerWidgetDto dto) throws Exception {
        SqliteUtil.update(
            "UPDATE " + TABLE_NAME + " SET PARENT_ID = ?, SORT_ORDER = ? WHERE ID = ?",
            dto.getParentId(),
            dto.getSortOrder() != null ? dto.getSortOrder() : 0,
            dto.getId());
    }
}
