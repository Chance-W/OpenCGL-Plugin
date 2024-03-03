package com.xtool.opencgl.dao;

import java.util.List;

import com.opencgl.base.utils.SqliteUtil;
import com.xtool.opencgl.model.RestWidgetDto;

@SuppressWarnings("unused")
public class RestWidgetDao {

    public void checkTable() throws Exception {
        if (!SqliteUtil.checkTableExist("REST_ITEM")) {
            SqliteUtil.update("CREATE TABLE REST_ITEM (ID INTEGER PRIMARY KEY AUTOINCREMENT,PARENT_ID INTEGER,NAME VARCHAR(150) NOT NULL,IS_LEAF BOOLEAN NOT NULL,REQUEST_METHOD VARCHAR,REQUEST_URL VARCHAR,REQUEST_HEADER VARCHAR,REQUEST_COOKIE VARCHAR,REQUEST_MEDIA_TYPE VARCHAR,INPUT_TEXT VARCHAR)");
        }
    }

    public List<RestWidgetDto> queryAllData() throws Exception {
        return SqliteUtil.queryForList("SELECT * FROM REST_ITEM ORDER BY ID", RestWidgetDto.class);
    }

    public Long insertData(RestWidgetDto restWidgetDto) throws Exception {
        return SqliteUtil.insert("INSERT INTO REST_ITEM(PARENT_ID, NAME, IS_LEAF, REQUEST_URL, REQUEST_METHOD, REQUEST_HEADER, REQUEST_COOKIE, REQUEST_MEDIA_TYPE, INPUT_TEXT)VALUES(? ,? ,? ,? ,? ,? ,? ,? ,?)",
            restWidgetDto.getParentId(),
            restWidgetDto.getName(),
            restWidgetDto.getIsLeaf(),
            restWidgetDto.getRequestUrl(),
            restWidgetDto.getRequestMethod(),
            restWidgetDto.getRequestHeader(),
            restWidgetDto.getRequestCookie(),
            restWidgetDto.getRequestMediaType(),
            restWidgetDto.getInputText());
    }

    public void delLevelData(RestWidgetDto restWidgetDto) throws Exception {
        SqliteUtil.delete("DELETE FROM REST_ITEM WHERE ID = ?", restWidgetDto.getId());
    }

    public void updateData(RestWidgetDto restWidgetDto) throws Exception {
        SqliteUtil.update("UPDATE REST_ITEM SET NAME=?, REQUEST_URL=?, REQUEST_METHOD=?, REQUEST_HEADER=?, REQUEST_COOKIE = ?, REQUEST_MEDIA_TYPE= ?,INPUT_TEXT = ? WHERE ID = ?",
            restWidgetDto.getName(),
            restWidgetDto.getRequestUrl(),
            restWidgetDto.getRequestMethod(),
            restWidgetDto.getRequestHeader(),
            restWidgetDto.getRequestCookie(),
            restWidgetDto.getRequestMediaType(),
            restWidgetDto.getInputText(),
            restWidgetDto.getId());
    }
}
