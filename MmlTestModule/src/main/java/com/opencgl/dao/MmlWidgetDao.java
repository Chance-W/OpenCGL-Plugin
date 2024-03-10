package com.opencgl.dao;

import java.util.List;

import com.opencgl.base.utils.SqliteUtil;
import com.opencgl.model.MmlWidgetDto;

/**
 * @author Chance.W
 */
public class MmlWidgetDao {

    public void checkTable() throws Exception {
        if (!SqliteUtil.checkTableExist("MML_ITEM")) {
            SqliteUtil.update("CREATE TABLE MML_ITEM (ID INTEGER PRIMARY KEY AUTOINCREMENT,PARENT_ID INTEGER,NAME VARCHAR(150) NOT NULL,IS_LEAF BOOLEAN NOT NULL,REQUEST_USERNAME VARCHAR, REQUEST_PASSWORD VARCHAR, REQUEST_IP VARCHAR, REQUEST_PORT VARCHAR, INPUT_TEXT VARCHAR)");
        }
    }

    public List<MmlWidgetDto> queryAllData() throws Exception {
        return SqliteUtil.queryForList("SELECT * FROM MML_ITEM", MmlWidgetDto.class);

    }

    public Long insertData(MmlWidgetDto mmlWidgetDto) throws Exception {

        return SqliteUtil.insert("INSERT INTO MML_ITEM(PARENT_ID, NAME, IS_LEAF, REQUEST_USERNAME, REQUEST_PASSWORD, REQUEST_IP, REQUEST_PORT, INPUT_TEXT)VALUES(?, ?, ? , ? , ? , ? , ? , ?)",
            mmlWidgetDto.getParentId(),
            mmlWidgetDto.getName(),
            mmlWidgetDto.getIsLeaf(),
            mmlWidgetDto.getRequestUsername(),
            mmlWidgetDto.getRequestPassword(),
            mmlWidgetDto.getRequestIp(),
            mmlWidgetDto.getRequestPort(),
            mmlWidgetDto.getInputText());
    }

    public void delLevelData(MmlWidgetDto mmlWidgetDto) throws Exception {
        SqliteUtil.update("DELETE FROM MML_ITEM WHERE ID = ?", mmlWidgetDto.getId());
    }

    public void updateData(MmlWidgetDto mmlWidgetDto) throws Exception {

        SqliteUtil.update("UPDATE MML_ITEM SET NAME=? , REQUEST_USERNAME=?, REQUEST_PASSWORD=?, REQUEST_IP=?, REQUEST_PORT=?, INPUT_TEXT=? WHERE ID = ?",
            mmlWidgetDto.getName(),
            mmlWidgetDto.getRequestUsername(),
            mmlWidgetDto.getRequestPassword(),
            mmlWidgetDto.getRequestIp(),
            mmlWidgetDto.getRequestPort(),
            mmlWidgetDto.getInputText());
    }
}
