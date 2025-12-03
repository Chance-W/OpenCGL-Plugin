package com.opencgl.base.service;


import java.util.List;

import org.apache.commons.collections.CollectionUtils;

import com.opencgl.base.model.BaseDataDto;
import com.opencgl.base.utils.i18n.BASE18N;
import com.opencgl.base.view.CustomizeTreeItem;

/**
 * @author Chance.W
 * @version 9.0
 * @description
 * @date 2023/2/22 8:48
 */
public interface TreeOperateService<T extends BaseDataDto> {

    CustomizeTreeItem<T> add(T treeDataDto);

    CustomizeTreeItem<T> importData(T treeDataDto);

    CustomizeTreeItem<T> delete(T treeDataDto);

    CustomizeTreeItem<T> update(T treeDataDto);

    void changeToDisplay(T treeDataDto);

    List<T> queryAll();

    default boolean supportImportAndExport() {
        return false;
    }

    default void validateImportData(List<T> importTreeDatas) {
        if (CollectionUtils.isEmpty(importTreeDatas)) {
            throw new RuntimeException(BASE18N.get("opencgl.base.import.data.check.error"));
        }
    }
}
