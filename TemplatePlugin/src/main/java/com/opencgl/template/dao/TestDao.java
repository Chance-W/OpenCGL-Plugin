package com.opencgl.template.dao;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencgl.base.model.BaseDataDto;
import com.opencgl.base.utils.SqliteUtil;
import com.opencgl.template.model.TestDataDto;

/**
 * @author Chance.W
 * @version 1.0
 * @CreateDate 2023/06/17 18:55
 * @since v9.0
 * 
 * 增加 SORT_ORDER 字段支持拖拽排序
 */
@SuppressWarnings("unused")
public class TestDao {
    private static final Logger logger = LoggerFactory.getLogger(TestDao.class);

    public static final String TEST_TREE_ITEM_TABLE = "TEST_TREE";

    public TestDao() {
        checkTable();
    }

    private void checkTable() {
        try {
            if (!SqliteUtil.checkTableExist(TEST_TREE_ITEM_TABLE)) {
                // 创建表，包含SORT_ORDER字段
                SqliteUtil.update("CREATE TABLE TEST_TREE (" +
                    "ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "PARENT_ID INTEGER," +
                    "NAME VARCHAR(150) NOT NULL," +
                    "IS_LEAF BOOLEAN NOT NULL," +
                    "TEXT VARCHAR," +
                    "SORT_ORDER INTEGER DEFAULT 0" +
                    ");");
                logger.info("创建表 {} 成功", TEST_TREE_ITEM_TABLE);
            } else {
                // 检查是否有SORT_ORDER字段，没有则添加
                try {
                    SqliteUtil.query("SELECT SORT_ORDER FROM " + TEST_TREE_ITEM_TABLE + " LIMIT 1");
                } catch (Exception e) {
                    // 字段不存在，添加它
                    SqliteUtil.update("ALTER TABLE " + TEST_TREE_ITEM_TABLE + " ADD COLUMN SORT_ORDER INTEGER DEFAULT 0;");
                    logger.info("为表 {} 添加 SORT_ORDER 字段成功", TEST_TREE_ITEM_TABLE);
                }
            }
        } catch (Exception e) {
            logger.error("检查/创建表失败", e);
        }
    }

    /**
     * 查询所有数据，按SORT_ORDER排序
     */
    public List<TestDataDto> queryAllData() {
        try {
            return SqliteUtil.queryForList(
                "SELECT ID, PARENT_ID, NAME, IS_LEAF, TEXT, SORT_ORDER FROM " + TEST_TREE_ITEM_TABLE + " ORDER BY SORT_ORDER, ID", 
                TestDataDto.class);
        } catch (Exception e) {
            logger.error("查询数据失败", e);
            return new ArrayList<>();
        }
    }

    public Integer getSequence() throws Exception {
        List<Integer> integers = SqliteUtil.queryForList("SELECT sql + 1 FROM sqlite_sequence WHERE name='TEST_TREE'", Integer.class);
        return integers.get(0);
    }

    public Long inset(TestDataDto dto) throws Exception {
        BaseDataDto base = dto;
        return SqliteUtil.insert(
            "INSERT INTO " + TEST_TREE_ITEM_TABLE + "(PARENT_ID, NAME, IS_LEAF, TEXT, SORT_ORDER) VALUES (?,?,?,?,?)",
            base.getParentId(),
            base.getName(),
            base.getIsLeaf(),
            dto.getText(),
            base.getSortOrder() != null ? base.getSortOrder() : 0);
    }

    public void update(TestDataDto dto) throws Exception {
        BaseDataDto base = dto;
        SqliteUtil.update(
            "UPDATE " + TEST_TREE_ITEM_TABLE + " SET NAME = ?, PARENT_ID = ?, TEXT = ?, SORT_ORDER = ? WHERE ID = ?",
            base.getName(),
            base.getParentId(),
            dto.getText(),
            base.getSortOrder() != null ? base.getSortOrder() : 0,
            base.getId());
    }

    public void delete(TestDataDto dto) throws Exception {
        SqliteUtil.update("DELETE FROM " + TEST_TREE_ITEM_TABLE + " WHERE ID = ?", dto.getId());
    }

    /**
     * 只更新位置信息（拖拽时使用）
     */
    public void updatePositionOnly(TestDataDto dto) throws Exception {
        SqliteUtil.update(
            "UPDATE " + TEST_TREE_ITEM_TABLE + " SET PARENT_ID = ?, SORT_ORDER = ? WHERE ID = ?",
            dto.getParentId(),
            dto.getSortOrder() != null ? dto.getSortOrder() : 0,
            dto.getId());
    }
}
