package com.opencgl.dao;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencgl.base.model.BaseDataDto;
import com.opencgl.base.utils.SqliteUtil;
import com.opencgl.model.RestWidgetDto;

/**
 * @author Chance.W
 * 
 * 增加 SORT_ORDER 字段支持拖拽排序
 */
@SuppressWarnings("unused")
public class RestWidgetDao {

    private static final Logger logger = LoggerFactory.getLogger(RestWidgetDao.class);
    private static final String TABLE_NAME = "REST_ITEM";

    public void checkTable() throws Exception {
        if (!SqliteUtil.checkTableExist(TABLE_NAME)) {
            // 创建表，包含SORT_ORDER字段
            SqliteUtil.update("CREATE TABLE " + TABLE_NAME + " (" +
                "ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                "PARENT_ID INTEGER," +
                "NAME VARCHAR(150) NOT NULL," +
                "IS_LEAF BOOLEAN NOT NULL," +
                "REQUEST_METHOD VARCHAR," +
                "REQUEST_URL VARCHAR," +
                "REQUEST_HEADER VARCHAR," +
                "REQUEST_COOKIE VARCHAR," +
                "REQUEST_MEDIA_TYPE VARCHAR," +
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
    public List<RestWidgetDto> queryAllData() throws Exception {
        return SqliteUtil.queryForList(
            "SELECT ID, PARENT_ID, NAME, IS_LEAF, REQUEST_METHOD, REQUEST_URL, REQUEST_HEADER, REQUEST_COOKIE, REQUEST_MEDIA_TYPE, INPUT_TEXT, SORT_ORDER FROM " + TABLE_NAME + " ORDER BY SORT_ORDER, ID", 
            RestWidgetDto.class);
    }

    public Long insertData(RestWidgetDto dto) throws Exception {
        BaseDataDto base = dto;
        return SqliteUtil.insert(
            "INSERT INTO " + TABLE_NAME + "(PARENT_ID, NAME, IS_LEAF, REQUEST_URL, REQUEST_METHOD, REQUEST_HEADER, REQUEST_COOKIE, REQUEST_MEDIA_TYPE, INPUT_TEXT, SORT_ORDER) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            base.getParentId(),
            base.getName(),
            base.getIsLeaf(),
            dto.getRequestUrl(),
            dto.getRequestMethod(),
            dto.getRequestHeader(),
            dto.getRequestCookie(),
            dto.getRequestMediaType(),
            dto.getInputText(),
            base.getSortOrder() != null ? base.getSortOrder() : 0);
    }

    public void delLevelData(RestWidgetDto dto) throws Exception {
        SqliteUtil.delete("DELETE FROM " + TABLE_NAME + " WHERE ID = ?", dto.getId());
    }

    public void updateData(RestWidgetDto dto) throws Exception {
        BaseDataDto base = dto;
        SqliteUtil.update(
            "UPDATE " + TABLE_NAME + " SET NAME=?, PARENT_ID=?, REQUEST_URL=?, REQUEST_METHOD=?, REQUEST_HEADER=?, REQUEST_COOKIE=?, REQUEST_MEDIA_TYPE=?, INPUT_TEXT=?, SORT_ORDER=? WHERE ID = ?",
            base.getName(),
            base.getParentId(),
            dto.getRequestUrl(),
            dto.getRequestMethod(),
            dto.getRequestHeader(),
            dto.getRequestCookie(),
            dto.getRequestMediaType(),
            dto.getInputText(),
            base.getSortOrder() != null ? base.getSortOrder() : 0,
            base.getId());
    }

    /**
     * 只更新位置信息（拖拽时使用）
     */
    public void updatePositionOnly(RestWidgetDto dto) throws Exception {
        SqliteUtil.update(
            "UPDATE " + TABLE_NAME + " SET PARENT_ID = ?, SORT_ORDER = ? WHERE ID = ?",
            dto.getParentId(),
            dto.getSortOrder() != null ? dto.getSortOrder() : 0,
            dto.getId());
    }
}
