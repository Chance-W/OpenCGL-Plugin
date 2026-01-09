package com.opencgl.ssh.dao;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencgl.base.model.BaseDataDto;
import com.opencgl.base.utils.SqliteUtil;
import com.opencgl.ssh.model.SshConnectionDto;

/**
 * SSH连接信息DAO
 * 支持SORT_ORDER字段的拖拽排序
 * 
 * @author Chance.W
 */
public class SshConnectionDao {

    private static final Logger logger = LoggerFactory.getLogger(SshConnectionDao.class);
    private static final String TABLE_NAME = "SSH_CONNECTION";

    public SshConnectionDao() {
        checkTable();
    }

    /**
     * 检查表是否存在，不存在则创建
     */
    private void checkTable() {
        try {
            if (!SqliteUtil.checkTableExist(TABLE_NAME)) {
                SqliteUtil.update("CREATE TABLE " + TABLE_NAME + " (" +
                    "ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "PARENT_ID INTEGER," +
                    "NAME VARCHAR(150) NOT NULL," +
                    "IS_LEAF BOOLEAN NOT NULL," +
                    "HOST VARCHAR(200)," +
                    "PORT INTEGER," +
                    "USERNAME VARCHAR(100)," +
                    "PASSWORD VARCHAR(200)," +
                    "PRIVATE_KEY_PATH VARCHAR(500)," +
                    "SORT_ORDER INTEGER DEFAULT 0" +
                    ")");
                logger.info("创建表 {} 成功", TABLE_NAME);
            } else {
                // 检查是否有SORT_ORDER字段
                try {
                    SqliteUtil.query("SELECT SORT_ORDER FROM " + TABLE_NAME + " LIMIT 1");
                } catch (Exception e) {
                    SqliteUtil.update("ALTER TABLE " + TABLE_NAME + " ADD COLUMN SORT_ORDER INTEGER DEFAULT 0;");
                    logger.info("为表 {} 添加 SORT_ORDER 字段成功", TABLE_NAME);
                }
            }
        } catch (Exception e) {
            logger.error("检查/创建表失败", e);
        }
    }

    /**
     * 查询所有数据，按SORT_ORDER排序
     */
    public List<SshConnectionDto> queryAllData() {
        try {
            return SqliteUtil.queryForList(
                "SELECT ID, PARENT_ID, NAME, IS_LEAF, HOST, PORT, USERNAME, PASSWORD, PRIVATE_KEY_PATH, SORT_ORDER FROM " 
                + TABLE_NAME + " ORDER BY SORT_ORDER, ID",
                SshConnectionDto.class);
        } catch (Exception e) {
            logger.error("查询数据失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 插入数据
     */
    public Long insert(SshConnectionDto dto) throws Exception {
        BaseDataDto base = dto;
        return SqliteUtil.insert(
            "INSERT INTO " + TABLE_NAME + "(PARENT_ID, NAME, IS_LEAF, HOST, PORT, USERNAME, PASSWORD, PRIVATE_KEY_PATH, SORT_ORDER) VALUES (?,?,?,?,?,?,?,?,?)",
            base.getParentId(),
            base.getName(),
            base.getIsLeaf(),
            dto.getHost(),
            dto.getPort(),
            dto.getUsername(),
            dto.getPassword(),
            dto.getPrivateKeyPath(),
            base.getSortOrder() != null ? base.getSortOrder() : 0);
    }

    /**
     * 更新数据
     */
    public void update(SshConnectionDto dto) throws Exception {
        BaseDataDto base = dto;
        SqliteUtil.update(
            "UPDATE " + TABLE_NAME + " SET NAME=?, PARENT_ID=?, HOST=?, PORT=?, USERNAME=?, PASSWORD=?, PRIVATE_KEY_PATH=?, SORT_ORDER=? WHERE ID=?",
            base.getName(),
            base.getParentId(),
            dto.getHost(),
            dto.getPort(),
            dto.getUsername(),
            dto.getPassword(),
            dto.getPrivateKeyPath(),
            base.getSortOrder() != null ? base.getSortOrder() : 0,
            base.getId());
    }

    /**
     * 删除数据
     */
    public void delete(SshConnectionDto dto) throws Exception {
        SqliteUtil.update("DELETE FROM " + TABLE_NAME + " WHERE ID = ?", dto.getId());
    }

    /**
     * 只更新位置信息（拖拽时使用）
     */
    public void updatePositionOnly(SshConnectionDto dto) throws Exception {
        SqliteUtil.update(
            "UPDATE " + TABLE_NAME + " SET PARENT_ID = ?, SORT_ORDER = ? WHERE ID = ?",
            dto.getParentId(),
            dto.getSortOrder() != null ? dto.getSortOrder() : 0,
            dto.getId());
    }
}
