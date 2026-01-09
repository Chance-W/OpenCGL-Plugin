package com.opencgl.scriptdebug.dao;

import com.opencgl.base.utils.SqliteUtil;
import com.opencgl.base.model.BaseDataDto;
import com.opencgl.scriptdebug.model.ScriptDebugTreeItem;

import java.util.List;

public class ScriptDebugWidgetDao {
    
    public static final String TABLE_NAME = "SCRIPT_DEBUG_TREE_ITEM";

    public ScriptDebugWidgetDao() {
        checkTable();
    }

    private void checkTable() {
        try {
            if (!SqliteUtil.checkTableExist(TABLE_NAME)) {
                SqliteUtil.update("CREATE TABLE " + TABLE_NAME + " (" +
                        "ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "PARENT_ID INTEGER," +
                        "NAME VARCHAR(150) NOT NULL," +
                        "IS_LEAF BOOLEAN NOT NULL," +
                        "LANGUAGE VARCHAR," +
                        "SCRIPT_CONTENT TEXT," +
                        "CONTEXT_JSON TEXT," +
                        "SORT_ORDER INTEGER DEFAULT 0" +
                        ")");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public Long insertData(ScriptDebugTreeItem dto) throws Exception {
        BaseDataDto base = dto;
        return SqliteUtil.insert(
            "INSERT INTO " + TABLE_NAME + "(PARENT_ID, NAME, IS_LEAF, LANGUAGE, SCRIPT_CONTENT, CONTEXT_JSON, SORT_ORDER) VALUES (?, ?, ?, ?, ?, ?, ?)",
            base.getParentId(),
            base.getName(),
            base.getIsLeaf(),
            dto.getLanguage(),
            dto.getScriptContent(),
            dto.getContextJson(),
            base.getSortOrder() != null ? base.getSortOrder() : 0);
    }
    
    public void updateData(ScriptDebugTreeItem dto) throws Exception {
        BaseDataDto base = dto;
        SqliteUtil.update(
            "UPDATE " + TABLE_NAME + " SET NAME=?, PARENT_ID=?, LANGUAGE=?, SCRIPT_CONTENT=?, CONTEXT_JSON=?, SORT_ORDER=? WHERE ID = ?",
            base.getName(),
            base.getParentId(),
            dto.getLanguage(),
            dto.getScriptContent(),
            dto.getContextJson(),
            base.getSortOrder() != null ? base.getSortOrder() : 0,
            base.getId());
    }

    public void delLevelData(ScriptDebugTreeItem dto) throws Exception {
        SqliteUtil.update("DELETE FROM " + TABLE_NAME + " WHERE ID = ?", dto.getId());
    }

    public void updatePositionOnly(ScriptDebugTreeItem dto) throws Exception {
         SqliteUtil.update(
            "UPDATE " + TABLE_NAME + " SET PARENT_ID = ?, SORT_ORDER = ? WHERE ID = ?",
            dto.getParentId(),
            dto.getSortOrder() != null ? dto.getSortOrder() : 0,
            dto.getId());
    }

    public List<ScriptDebugTreeItem> queryAllData() throws Exception {
        return SqliteUtil.queryForList(
            "SELECT * FROM " + TABLE_NAME + " ORDER BY SORT_ORDER, ID", 
            ScriptDebugTreeItem.class);
    }
}
