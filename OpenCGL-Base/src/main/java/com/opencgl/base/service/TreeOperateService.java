package com.opencgl.base.service;


import java.util.List;

import org.apache.commons.collections.CollectionUtils;

import com.opencgl.base.model.BaseDataDto;
import com.opencgl.base.utils.i18n.BaseI18N;
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

    /**
     * 是否支持导入导出功能
     */
    default boolean supportImportAndExport() {
        return false;
    }

    /**
     * 是否支持拖拽排序功能
     */
    default boolean supportDragDrop() {
        return true;
    }

    /**
     * 数据变更通知
     * @param oldParentId 旧的父节点ID
     * @param newParentId 新的父节点ID
     */
    default void notifyDataChange(Long oldParentId, Long newParentId) {
        // 默认不需要做任何处理
    }
    
    /**
     * 是否支持自定义操作（如连接、打开等）
     * @return true 表示支持，会在右键菜单中显示对应操作项
     */
    default boolean supportAction() {
        return false;
    }
    
    /**
     * 获取自定义操作的名称
     * @return 操作名称，如"连接"、"打开"等
     */
    default String getActionName() {
        return "操作";
    }
    
    /**
     * 执行自定义操作
     * @param data 要操作的数据对象
     */
    default void performAction(T data) {
        // 默认不执行任何操作
    }

    /**
     * 移动节点到新的父节点（只更新parentId）
     * @param item 要移动的节点
     * @param newParentId 新的父节点ID
     */
    default void move(T item, Long newParentId) {
        item.setParentId(newParentId);
        updatePositionOnly(item);
    }

    default CustomizeTreeItem<T> copy(T item) {
       return null;
    }

    /**
     * 重新排序单个节点（只更新sortOrder）
     * @param item 节点
     * @param newSortOrder 新的排序号
     */
    default void reorder(T item, int newSortOrder) {
        item.setSortOrder(newSortOrder);
        updatePositionOnly(item);
    }

    /**
     * 批量重排序（拖拽后只更新排序号）
     * @param siblings 同级节点列表，按新顺序排列
     */
    default void batchReorder(List<T> siblings) {
        for (int i = 0; i < siblings.size(); i++) {
            T item = siblings.get(i);
            if (item.getSortOrder() == null || item.getSortOrder() != i) {
                item.setSortOrder(i);
                updatePositionOnly(item);
            }
        }
    }

    /**
     * 只更新节点位置信息（parentId和sortOrder）
     * 子类应覆盖此方法以避免业务字段被覆盖
     * @param item 节点（只需要id, parentId, sortOrder有效）
     */
    default void updatePositionOnly(T item) {
        // 默认实现：不做任何操作（避免数据丢失）
        // 子类需要实现具体的SQL: UPDATE table SET PARENT_ID=?, SORT_ORDER=? WHERE ID=?
    }

    /**
     * 验证导入数据
     */
    default void validateImportData(List<T> importTreeDatas) {
        if (CollectionUtils.isEmpty(importTreeDatas)) {
            throw new RuntimeException(BaseI18N.get("opencgl.base.import.data.check.error"));
        }
    }
}

