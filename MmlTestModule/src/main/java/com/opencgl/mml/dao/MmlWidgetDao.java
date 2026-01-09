package com.opencgl.mml.dao;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencgl.base.model.BaseDataDto;
import com.opencgl.base.utils.SqliteUtil;
import com.opencgl.mml.model.MmlWidgetDto;

/**
 * @author Chance.W
 * 
 * 增加 SORT_ORDER 字段支持拖拽排序
 */
public class MmlWidgetDao {
    
    private static final Logger logger = LoggerFactory.getLogger(MmlWidgetDao.class);
    private static final String TABLE_NAME = "MML_ITEM";

    public void checkTable() throws Exception {
        if (!SqliteUtil.checkTableExist(TABLE_NAME)) {
            // 创建表，包含SORT_ORDER字段
            SqliteUtil.update("CREATE TABLE " + TABLE_NAME + " (" +
                "ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                "PARENT_ID INTEGER," +
                "NAME VARCHAR(150) NOT NULL," +
                "IS_LEAF BOOLEAN NOT NULL," +
                "REQUEST_USERNAME VARCHAR," +
                "REQUEST_PASSWORD VARCHAR," +
                "REQUEST_IP VARCHAR," +
                "REQUEST_PORT VARCHAR," +
                "INPUT_TEXT VARCHAR," +
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
    public List<MmlWidgetDto> queryAllData() throws Exception {
        return SqliteUtil.queryForList(
            "SELECT ID, PARENT_ID, NAME, IS_LEAF, REQUEST_USERNAME, REQUEST_PASSWORD, REQUEST_IP, REQUEST_PORT, INPUT_TEXT, SORT_ORDER FROM " + TABLE_NAME + " ORDER BY SORT_ORDER, ID", 
            MmlWidgetDto.class);
    }

    public Long insertData(MmlWidgetDto dto) throws Exception {
        BaseDataDto base = dto;
        return SqliteUtil.insert(
            "INSERT INTO " + TABLE_NAME + "(PARENT_ID, NAME, IS_LEAF, REQUEST_USERNAME, REQUEST_PASSWORD, REQUEST_IP, REQUEST_PORT, INPUT_TEXT, SORT_ORDER) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)",
            base.getParentId(),
            base.getName(),
            base.getIsLeaf(),
            dto.getRequestUsername(),
            dto.getRequestPassword(),
            dto.getRequestIp(),
            dto.getRequestPort(),
            dto.getInputText(),
            base.getSortOrder() != null ? base.getSortOrder() : 0);
    }

    public void delLevelData(MmlWidgetDto dto) throws Exception {
        SqliteUtil.update("DELETE FROM " + TABLE_NAME + " WHERE ID = ?", dto.getId());
    }

    public void updateData(MmlWidgetDto dto) throws Exception {
        BaseDataDto base = dto;
        SqliteUtil.update(
            "UPDATE " + TABLE_NAME + " SET NAME=?, PARENT_ID=?, REQUEST_USERNAME=?, REQUEST_PASSWORD=?, REQUEST_IP=?, REQUEST_PORT=?, INPUT_TEXT=?, SORT_ORDER=? WHERE ID = ?",
            base.getName(),
            base.getParentId(),
            dto.getRequestUsername(),
            dto.getRequestPassword(),
            dto.getRequestIp(),
            dto.getRequestPort(),
            dto.getInputText(),
            base.getSortOrder() != null ? base.getSortOrder() : 0,
            base.getId());
    }

    /**
     * 只更新位置信息（拖拽时使用）
     */
    public void updatePositionOnly(MmlWidgetDto dto) throws Exception {
        SqliteUtil.update(
            "UPDATE " + TABLE_NAME + " SET PARENT_ID = ?, SORT_ORDER = ? WHERE ID = ?",
            dto.getParentId(),
            dto.getSortOrder() != null ? dto.getSortOrder() : 0,
            dto.getId());
    }
}
