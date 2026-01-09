package com.opencgl.dao;

import com.opencgl.base.utils.SqliteUtil;
import com.opencgl.model.RestMockServerWidgetDto;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class RestMockServerWidgetDao {

    private static final Logger logger = LoggerFactory.getLogger(RestMockServerWidgetDao.class);

    public void checkTable() throws Exception {
        if (!SqliteUtil.checkTableExist("REST_MOCK_ITEM")) {
            SqliteUtil.update("CREATE TABLE REST_MOCK_ITEM (" +
                    "ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "IS_ENABLE BOOLEAN default(0)," +
                    "CONTENT_PATH VARCHAR unique NOT NULL," +
                    "RESPONSE_HEADER VARCHAR," +
                    "RESPONSE_CONTENT VARCHAR," +
                    "DESCRIPTION VARCHAR," +
                    "HTTP_METHOD VARCHAR DEFAULT 'GET'," +
                    "STATUS_CODE INTEGER DEFAULT 200," +
                    "DELAY_MS INTEGER DEFAULT 0)");
        } else {
            // 兼容旧表：新增列（如果不存在）
            safeAddColumn("REST_MOCK_ITEM", "HTTP_METHOD", "VARCHAR DEFAULT 'GET'");
            safeAddColumn("REST_MOCK_ITEM", "STATUS_CODE", "INTEGER DEFAULT 200");
            safeAddColumn("REST_MOCK_ITEM", "DELAY_MS", "INTEGER DEFAULT 0");
        }
    }

    /**
     * 安全地为表添加新列，如果列已存在则忽略异常
     */
    private void safeAddColumn(String table, String column, String type) {
        try {
            SqliteUtil.update("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        } catch (Exception e) {
            // 列已存在，忽略
            logger.debug("列 {} 已存在或添加失败: {}", column, e.getMessage());
        }
    }

    private static final String ALL_COLUMNS = "IS_ENABLE, CONTENT_PATH, RESPONSE_HEADER, RESPONSE_CONTENT, DESCRIPTION, HTTP_METHOD, STATUS_CODE, DELAY_MS";

    public List<RestMockServerWidgetDto> queryAllData() throws Exception {
        return SqliteUtil.queryForList(
                "SELECT " + ALL_COLUMNS + " FROM REST_MOCK_ITEM",
                RestMockServerWidgetDto.class);
    }

    public void insertData(RestMockServerWidgetDto dto) throws Exception {
        SqliteUtil.update(
                "INSERT INTO REST_MOCK_ITEM(IS_ENABLE, CONTENT_PATH, RESPONSE_HEADER, RESPONSE_CONTENT, DESCRIPTION, HTTP_METHOD, STATUS_CODE, DELAY_MS) VALUES(?,?,?,?,?,?,?,?)",
                dto.getIsEnable(),
                dto.getContentPath(),
                dto.getResponseHeader(),
                dto.getResponseContent(),
                dto.getDescription(),
                dto.getHttpMethod() != null ? dto.getHttpMethod() : "GET",
                dto.getStatusCode() != null ? dto.getStatusCode() : 200,
                dto.getDelayMs() != null ? dto.getDelayMs() : 0);
    }

    public void delLevelData(RestMockServerWidgetDto dto) throws Exception {
        SqliteUtil.update("DELETE FROM REST_MOCK_ITEM WHERE CONTENT_PATH = ?",
                dto.getContentPath());
    }

    public void updateData(RestMockServerWidgetDto dto, String oldContextPath) throws Exception {
        SqliteUtil.update(
                "UPDATE REST_MOCK_ITEM SET IS_ENABLE=?, CONTENT_PATH=?, RESPONSE_HEADER=?, RESPONSE_CONTENT=?, DESCRIPTION=?, HTTP_METHOD=?, STATUS_CODE=?, DELAY_MS=? WHERE CONTENT_PATH=?",
                dto.getIsEnable(),
                dto.getContentPath(),
                dto.getResponseHeader(),
                dto.getResponseContent(),
                dto.getDescription(),
                dto.getHttpMethod() != null ? dto.getHttpMethod() : "GET",
                dto.getStatusCode() != null ? dto.getStatusCode() : 200,
                dto.getDelayMs() != null ? dto.getDelayMs() : 0,
                oldContextPath);
    }

    public Boolean queryIfExistByName(String contentPath) throws Exception {
        List<RestMockServerWidgetDto> list = SqliteUtil.queryForList(
                "SELECT " + ALL_COLUMNS + " FROM REST_MOCK_ITEM WHERE CONTENT_PATH = ?",
                RestMockServerWidgetDto.class, contentPath);
        return list.isEmpty();
    }

    public String queryByContentPath(String contentPath) throws Exception {
        List<RestMockServerWidgetDto> list = SqliteUtil.queryForList(
                "SELECT " + ALL_COLUMNS + " FROM REST_MOCK_ITEM WHERE CONTENT_PATH = ?",
                RestMockServerWidgetDto.class, contentPath);
        return list.get(0).getResponseContent();
    }
}
