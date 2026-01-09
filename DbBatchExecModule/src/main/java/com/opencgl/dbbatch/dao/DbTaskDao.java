package com.opencgl.dbbatch.dao;

import com.opencgl.base.utils.SqliteUtil;
import com.opencgl.dbbatch.model.DbTaskDto;

import java.util.List;
import java.util.UUID;

/**
 * 数据库任务 DAO
 */
public class DbTaskDao {

    private static final String TABLE_NAME = "DB_TASK";

    public void checkTable() throws Exception {
        if (!SqliteUtil.checkTableExist(TABLE_NAME)) {
            String sql = "CREATE TABLE " + TABLE_NAME + " (" +
                    "ID VARCHAR(50) PRIMARY KEY," +
                    "PARENT_ID VARCHAR(50)," +
                    "NAME VARCHAR(200)," +
                    "IS_GROUP INT," + // 0: false, 1: true
                    "DB_TYPE VARCHAR(20)," +
                    "HOST VARCHAR(100)," +
                    "PORT VARCHAR(10)," +
                    "USERNAME VARCHAR(100)," +
                    "PASSWORD VARCHAR(100)," +
                    "DATABASE_NAME VARCHAR(100)," +
                    "SQL_CONTENT TEXT," +
                    "REPLACE_CONFIG TEXT," +
                    "DESCRIPTION TEXT" +
                    ")";
            SqliteUtil.update(sql);
        }
    }
    
    public List<DbTaskDto> queryAll() throws Exception {
        String sql = "SELECT * FROM " + TABLE_NAME;
        return SqliteUtil.queryForList(sql, DbTaskDto.class);
    }
    
    public void insert(DbTaskDto dto) throws Exception {
        if (dto.getId() == null) dto.setId(UUID.randomUUID().toString());
        String sql = "INSERT INTO " + TABLE_NAME + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        SqliteUtil.update(sql, 
            dto.getId(),
            dto.getParentId(),
            dto.getName(),
            dto.isGroup() ? 1 : 0,
            dto.getDbType(),
            dto.getHost(),
            dto.getPort(),
            dto.getUsername(),
            dto.getPassword(),
            dto.getDatabase(),
            dto.getSqlContent(),
            dto.getReplaceConfig(),
            dto.getDescription()
        );
    }
    
    public void update(DbTaskDto dto) throws Exception {
        String sql = "UPDATE " + TABLE_NAME + " SET NAME=?, PARENT_ID=?, IS_GROUP=?, DB_TYPE=?, HOST=?, PORT=?, USERNAME=?, PASSWORD=?, DATABASE_NAME=?, SQL_CONTENT=?, REPLACE_CONFIG=?, DESCRIPTION=? WHERE ID=?";
        SqliteUtil.update(sql, 
            dto.getName(),
            dto.getParentId(),
            dto.isGroup() ? 1 : 0,
            dto.getDbType(),
            dto.getHost(),
            dto.getPort(),
            dto.getUsername(),
            dto.getPassword(),
            dto.getDatabase(),
            dto.getSqlContent(),
            dto.getReplaceConfig(),
            dto.getDescription(),
            dto.getId()
        );
    }
    
    public void delete(String id) throws Exception {
        String sql = "DELETE FROM " + TABLE_NAME + " WHERE ID=?";
        SqliteUtil.update(sql, id);
    }
}
