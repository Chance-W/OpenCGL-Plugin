package com.xtool.opencgl.dao;

import com.opencgl.base.utils.SqliteUtil;
import com.xtool.opencgl.model.RestMockServerWidgetDto;

import java.util.List;

/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class RestMockServerWidgetDao {

    public void checkTable() throws Exception {
        if (!SqliteUtil.checkTableExist("REST_MOCK_ITEM")) {
            SqliteUtil.update("CREATE TABLE REST_MOCK_ITEM (ID INTEGER PRIMARY KEY AUTOINCREMENT,IS_ENABLE BOOLEAN default(0),CONTENT_PATH VARCHAR unique NOT NULL,RESPONSE_HEADER VARCHAR,RESPONSE_CONTENT VARCHAR,DESCRIPTION VARCHAR)");
        }
    }

    public List<RestMockServerWidgetDto> queryAllData() throws Exception {
        return SqliteUtil.queryForList("SELECT IS_ENABLE ,CONTENT_PATH ,RESPONSE_HEADER ,RESPONSE_CONTENT ,DESCRIPTION FROM REST_MOCK_ITEM", RestMockServerWidgetDto.class);
    }

    public void insertData(RestMockServerWidgetDto restMockServerWidgetDto) throws Exception {
        SqliteUtil.update("INSERT INTO REST_MOCK_ITEM( IS_ENABLE ,CONTENT_PATH ,RESPONSE_HEADER ,RESPONSE_CONTENT ,DESCRIPTION )VALUES(? , ? , ? , ? , ?)",
            restMockServerWidgetDto.getIsEnable(),
            restMockServerWidgetDto.getContentPath(),
            restMockServerWidgetDto.getResponseHeader(),
            restMockServerWidgetDto.getResponseContent(),
            restMockServerWidgetDto.getDescription());

    }

    public void delLevelData(RestMockServerWidgetDto restMockServerWidgetDto) throws Exception {
        SqliteUtil.update("DELETE FROM REST_MOCK_ITEM WHERE CONTENT_PATH = ?",
            restMockServerWidgetDto.getContentPath());
    }


    public void updateData(RestMockServerWidgetDto restMockServerWidgetDto, String oldContextPath) throws Exception {
        SqliteUtil.update("UPDATE REST_MOCK_ITEM SET IS_ENABLE= ? , CONTENT_PATH =?,RESPONSE_HEADER =?,RESPONSE_CONTENT =?,DESCRIPTION = ? WHERE CONTENT_PATH = ?",
            restMockServerWidgetDto.getIsEnable(),
            restMockServerWidgetDto.getContentPath(),
            restMockServerWidgetDto.getResponseHeader(),
            restMockServerWidgetDto.getResponseContent(),
            restMockServerWidgetDto.getDescription(),
            oldContextPath);
    }

    public Boolean queryIfExistByName(String contentPath) throws Exception {
        List<RestMockServerWidgetDto> restMockServerWidgetDtos = SqliteUtil.queryForList("SELECT  IS_ENABLE,CONTENT_PATH ,RESPONSE_HEADER ,RESPONSE_CONTENT ,DESCRIPTION FROM REST_MOCK_ITEM WHERE CONTENT_PATH = ?", RestMockServerWidgetDto.class, contentPath);
        return restMockServerWidgetDtos.isEmpty();
    }

    public String queryByContentPath(String contentPath) throws Exception {
        List<RestMockServerWidgetDto> restMockServerWidgetDtos = SqliteUtil.queryForList("SELECT IS_ENABLE ,CONTENT_PATH ,RESPONSE_HEADER ,RESPONSE_CONTENT ,DESCRIPTION FROM REST_MOCK_ITEM WHERE CONTENT_PATH = ?", RestMockServerWidgetDto.class, contentPath);
        return restMockServerWidgetDtos.get(0).getResponseContent();
    }

}
