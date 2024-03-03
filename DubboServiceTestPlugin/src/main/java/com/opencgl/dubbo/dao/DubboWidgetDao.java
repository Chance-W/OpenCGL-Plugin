package com.opencgl.dubbo.dao;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencgl.base.utils.SqliteUtil;
import com.opencgl.dubbo.model.DubboTreeItem;


/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class DubboWidgetDao {
    private static final Logger logger = LoggerFactory.getLogger(DubboWidgetDao.class);
    public static final String DUBBO_TREE_ITEM_TABLE = "DUBBO_TREE_ITEM";

    public DubboWidgetDao() {
        checkTable();
    }

    private void checkTable() {
        try {
            if (!SqliteUtil.checkTableExist(DUBBO_TREE_ITEM_TABLE)) {
                SqliteUtil.update("CREATE TABLE DUBBO_TREE_ITEM (ID INTEGER PRIMARY KEY AUTOINCREMENT,PARENT_ID INTEGER,NAME VARCHAR(150) NOT NULL,IS_LEAF BOOLEAN NOT NULL,ENVNAME VARCHAR,INTERFACEINFO VARCHAR,METHODINFO VARCHAR,IS_SELECTED BOOLEAN default(0),REQUEST_TYPE VARCHAR,INPUTTEXT VARCHAR)");
            }
        }
        catch (Exception e) {
            logger.error("", e);
        }
    }

    public List<DubboTreeItem> queryAllData() throws Exception {
        return SqliteUtil.queryForList("SELECT ID, PARENT_ID, NAME, IS_LEAF, ENVNAME, INTERFACEINFO, METHODINFO, IS_SELECTED, REQUEST_TYPE, INPUTTEXT FROM DUBBO_TREE_ITEM ORDER BY ID", DubboTreeItem.class);

    }

    public Long insertData(DubboTreeItem dubboTreeItem) throws Exception {
        return SqliteUtil.insert(
            "INSERT INTO DUBBO_TREE_ITEM(PARENT_ID, NAME, IS_LEAF, ENVNAME, INTERFACEINFO, METHODINFO, IS_SELECTED, REQUEST_TYPE, INPUTTEXT) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            dubboTreeItem.getParentId(),
            dubboTreeItem.getName(),
            dubboTreeItem.getIsLeaf(),
            dubboTreeItem.getEnvName(),
            dubboTreeItem.getInterfaceInfo(),
            dubboTreeItem.getMethodInfo(),
            dubboTreeItem.getIsSelected(),
            dubboTreeItem.getRequestType(),
            dubboTreeItem.getInputText());

    }

    public void delLevelData(DubboTreeItem dubboTreeItem) throws Exception {
        SqliteUtil.update("DELETE FROM DUBBO_TREE_ITEM WHERE ID = ?", dubboTreeItem.getId());
    }

    public void updateLevelData(Long newParentId, DubboTreeItem where) throws Exception {
        SqliteUtil.update("UPDATE DUBBO_TREE_ITEM SET PARENT_ID= ? WHERE ID = ?", newParentId, where.getId());

    }

    public void updateData(DubboTreeItem dubboTreeItem) throws Exception {
        SqliteUtil.update("UPDATE DUBBO_TREE_ITEM SET NAME=?, ENVNAME=?, INTERFACEINFO=?, METHODINFO=?, IS_SELECTED = ?, REQUEST_TYPE = ?, INPUTTEXT = ? WHERE ID = ?",
            dubboTreeItem.getName(),
            dubboTreeItem.getEnvName(),
            dubboTreeItem.getInterfaceInfo(),
            dubboTreeItem.getMethodInfo(),
            dubboTreeItem.getIsSelected(),
            dubboTreeItem.getRequestType(),
            dubboTreeItem.getInputText(),
            dubboTreeItem.getId());
    }


    public DubboTreeItem queryData(DubboTreeItem dubboTreeItem) throws Exception {
        return SqliteUtil.queryForList("SELECT ENVNAME, INTERFACEINFO, METHODINFO, IS_SELECTED, REQUEST_TYPE, INPUTTEXT FROM DUBBO_TREE_ITEM WHERE ID = ?",
                DubboTreeItem.class,
                dubboTreeItem.getId())
            .get(0);
    }
}

