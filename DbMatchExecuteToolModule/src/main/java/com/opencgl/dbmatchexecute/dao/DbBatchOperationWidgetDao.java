package com.opencgl.dbmatchexecute.dao;

import java.util.List;

import com.opencgl.base.utils.SqliteUtil;
import com.opencgl.dbmatchexecute.model.DbBatchOperWidgetTextDto;

/**
 * @author Chance.W
 */
public class DbBatchOperationWidgetDao {


    public void checkTable() throws Exception {
        if (!SqliteUtil.checkTableExist("DB_SUFFIX_ITEM")) {
            SqliteUtil.update("CREATE TABLE DB_SUFFIX_ITEM (ID INTEGER PRIMARY KEY AUTOINCREMENT,MODULE_DB_INFO VARCHAR,MODULE_DB_TABLE_NAME VARCHAR,MODULE_SUFFIX VARCHAR)");
        }
    }

    /**
     * 查询全量记录
     */
    public List<DbBatchOperWidgetTextDto> queryAllData() throws Exception {
        return SqliteUtil.queryForList("SELECT MODULE_DB_INFO,MODULE_DB_TABLE_NAME,MODULE_SUFFIX FROM DB_SUFFIX_ITEM ORDER BY ID DESC", DbBatchOperWidgetTextDto.class);
    }

    /**
     * 查询指定记录
     */
    public int queryData(DbBatchOperWidgetTextDto dbBatchOperWidgetTextDto) throws Exception {
        return SqliteUtil.queryForList("SELECT MODULE_DB_INFO,MODULE_DB_TABLE_NAME,MODULE_SUFFIX FROM DB_SUFFIX_ITEM WHERE MODULE_DB_INFO = ? AND MODULE_DB_TABLE_NAME = ? AND MODULE_SUFFIX = ?",
            DbBatchOperWidgetTextDto.class,
            dbBatchOperWidgetTextDto.getModuleDbInfo(),
            dbBatchOperWidgetTextDto.getModuleDbTableName(),
            dbBatchOperWidgetTextDto.getModuleSuffix()).size();
    }


    /**
     * 插入新增记录
     */
    public void insertData(DbBatchOperWidgetTextDto dbBatchOperWidgetTextDto) throws Exception {
        SqliteUtil.insert("INSERT INTO DB_SUFFIX_ITEM(MODULE_DB_INFO,MODULE_DB_TABLE_NAME,MODULE_SUFFIX)VALUES(?, ?, ?)",
            dbBatchOperWidgetTextDto.getModuleDbInfo(),
            dbBatchOperWidgetTextDto.getModuleDbTableName(),
            dbBatchOperWidgetTextDto.getModuleSuffix());

    }

    /**
     * 删除指定记录
     */
    public void delLevelData(DbBatchOperWidgetTextDto dbBatchOperWidgetTextDto) throws Exception {
        SqliteUtil.delete("DELETE FROM DB_SUFFIX_ITEM WHERE MODULE_DB_INFO = ? AND MODULE_DB_TABLE_NAME = ? AND MODULE_SUFFIX = ?",
            dbBatchOperWidgetTextDto.getModuleDbInfo(),
            dbBatchOperWidgetTextDto.getModuleDbTableName(),
            dbBatchOperWidgetTextDto.getModuleSuffix());
    }
}

