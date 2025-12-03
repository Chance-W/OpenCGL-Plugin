package com.opencgl.template.dao;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencgl.base.utils.SqliteUtil;
import com.opencgl.template.model.TestDataDto;

/**
 * @author Chance.W
 * @version 1.0
 * @CreateDate 2023/06/17 18:55
 * @since v9.0
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
                SqliteUtil.update("CREATE TABLE TEST_TREE (ID INTEGER PRIMARY KEY AUTOINCREMENT,PARENT_ID INTEGER,NAME VARCHAR(150) NOT NULL,IS_LEAF BOOLEAN NOT NULL,TEXT VARCHAR);");
            }
        }
        catch (Exception e) {
            logger.error("", e);
        }
    }


    public List<TestDataDto> queryAllData() {
        try {
            return SqliteUtil.queryForList("SELECT* FROM TEST_TREE ORDER BY ID", TestDataDto.class);
        }
        catch (Exception e) {
            logger.error("", e);
            return new ArrayList<>();
        }
    }

    public Integer getSequence() throws Exception {
        List<Integer> integers = SqliteUtil.queryForList("SELECT sql + 1 FROM sqlite_sequence WHERE name='TEST_TREE'", Integer.class);
        return integers.get(0);
    }

    public Long inset(TestDataDto TestDataDto) throws Exception {
        return SqliteUtil.insert("INSERT INTO TEST_TREE(PARENT_ID,NAME,IS_LEAF,TEXT) VALUES (?,?,?,?)",
            TestDataDto.getParentId(),
            TestDataDto.getName(),
            TestDataDto.getIsLeaf(),
            TestDataDto.getText());
    }

    public void update(TestDataDto TestDataDto) throws Exception {
        SqliteUtil.update("UPDATE TEST_TREE SET NAME = ?,PARENT_ID= ?,TEXT = ? WHERE ID = ?",
            TestDataDto.getName(),
            TestDataDto.getParentId(),
            TestDataDto.getText(),
            TestDataDto.getId());
    }

    public void delete(TestDataDto TestDataDto) throws Exception {
        SqliteUtil.update("DELETE FROM TEST_TREE WHERE ID = ?", TestDataDto.getId());
    }
}
