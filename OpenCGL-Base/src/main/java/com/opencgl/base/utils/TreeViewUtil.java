/*
package com.opencgl.base.utils;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson2.util.DateUtils;
import com.opencgl.base.model.BaseDataDto;
import com.opencgl.base.service.TreeOperateService;
import com.opencgl.base.utils.i18n.BaseI18N;
import com.opencgl.base.view.CustomizeTreeItem;
import com.sun.javafx.scene.control.skin.resources.ControlResources;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Border;
import javafx.scene.control.Tooltip;
import javafx.util.Callback;

*/
/**
 * @author Chance.W
 * @see com.opencgl.base.utils.tree.TreeViewBuilder
 * @deprecated 请使用 {@link com.opencgl.base.utils.tree.TreeViewBuilder} 替代
 * 新版本支持拖拽排序、排序持久化等功能
 *//*

@Deprecated(since = "2.0", forRemoval = false)
@SuppressWarnings({"unused", "DanglingJavadoc"})
public class TreeViewUtil<T extends BaseDataDto> {
    private static final Logger logger = LoggerFactory.getLogger(TreeViewUtil.class);

    // 拖动相关的数据格式
    // private static final DataFormat DRAG_DATA_FORMAT = new DataFormat("tree-item-drag");

    */
/**
     * 验证拖动操作是否有效
     *
     * @param draggedItem 被拖动的节点
     * @param targetItem  目标节点
     * @return 是否允许拖动
     *//*

    private static <T extends BaseDataDto> boolean isValidDragOperation(TreeItem<T> draggedItem, TreeItem<T> targetItem) {
        if (draggedItem == null || targetItem == null) {
            return false;
        }

        // 不能拖动到自己
        if (draggedItem == targetItem) {
            return false;
        }

        // 不能拖动到自己的子节点
        if (isDescendant(draggedItem, targetItem)) {
            return false;
        }

        T draggedData = draggedItem.getValue();
        T targetData = targetItem.getValue();

        // 如果是同父节点，允许排序操作（包括子节点间插入）
        if (draggedItem.getParent() == targetItem.getParent()) {
            return true;
        }

        // 目录节点只能拖动到目录节点下
        if (!draggedData.getIsLeaf()) {
            return !targetData.getIsLeaf();
        }

        // 非目录节点只能拖动到目录节点下
        if (draggedData.getIsLeaf()) {
            return !targetData.getIsLeaf();
        }

        return false;
    }

    */
/**
     * 检查 targetItem 是否是 draggedItem 的后代节点
     *//*

    private static <T extends BaseDataDto> boolean isDescendant(TreeItem<T> draggedItem, TreeItem<T> targetItem) {
        TreeItem<T> parent = targetItem.getParent();
        while (parent != null) {
            if (parent == draggedItem) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    */
/**
     * 执行拖动操作
     *//*

    private static <T extends BaseDataDto> void performDragOperation(TreeView<T> treeView, TreeItem<T> draggedItem, TreeItem<T> targetItem, TreeOperateService<T> treeOperateService) {
        if (!isValidDragOperation(draggedItem, targetItem)) {
            return;
        }

        T draggedData = draggedItem.getValue();
        T targetData = targetItem.getValue();

        // 更新父节点ID
        draggedData.setParentId(targetData.getId());

        // 调用服务更新数据
        try {
            treeOperateService.update(draggedData);

            // 从原位置移除
            TreeItem<T> oldParent = draggedItem.getParent();
            if (oldParent != null) {
                oldParent.getChildren().remove(draggedItem);
            }

            // 添加到新位置
            targetItem.getChildren().add(draggedItem);

            // 展开目标节点
            targetItem.setExpanded(true);

            // 刷新树视图
            treeView.refresh();

            logger.info("Successfully moved item '{}' to '{}'", draggedData.getName(), targetData.getName());
        }
        catch (Exception e) {
            logger.error("Failed to perform drag operation", e);
            // 可以在这里显示错误提示
        }
    }

    */
/**
     * 执行排序操作
     *//*

    private static <T extends BaseDataDto> void performSortOperation(TreeView<T> treeView, TreeItem<T> draggedItem, TreeItem<T> targetItem, TreeOperateService<T> treeOperateService, boolean insertAfter) {
        if (draggedItem == null || targetItem == null || draggedItem == targetItem) {
            return;
        }

        TreeItem<T> parent = draggedItem.getParent();
        if (parent == null || parent != targetItem.getParent()) {
            return;
        }

        // 获取父节点的子节点列表
        var children = parent.getChildren();
        int draggedIndex = children.indexOf(draggedItem);
        int targetIndex = children.indexOf(targetItem);

        if (draggedIndex == -1 || targetIndex == -1) {
            return;
        }

        // 移除被拖动的节点
        children.remove(draggedItem);

        // 计算新的插入位置
        int newIndex;
        if (draggedIndex < targetIndex) {
            // 向下拖动
            newIndex = insertAfter ? targetIndex : targetIndex - 1;
        }
        else {
            // 向上拖动
            newIndex = insertAfter ? targetIndex + 1 : targetIndex;
        }

        // 确保索引在有效范围内
        newIndex = Math.max(0, Math.min(newIndex, children.size()));

        // 插入到新位置
        children.add(newIndex, draggedItem);

        // 刷新树视图
        treeView.refresh();

        logger.info("Successfully reordered item '{}' to position {} (insertAfter: {})",
            draggedItem.getValue().getName(), newIndex, insertAfter);
    }

    */
/**
     * 执行子节点间插入操作
     *//*

    private static <T extends BaseDataDto> void performChildInsertion(TreeView<T> treeView, TreeItem<T> draggedItem, TreeItem<T> targetItem, TreeOperateService<T> treeOperateService, int childIndex) {
        if (draggedItem == null || targetItem == null || draggedItem == targetItem) {
            return;
        }

        // 获取目标节点的子节点列表
        var children = targetItem.getChildren();

        // 确定插入位置
        int insertIndex = childIndex;
        if (insertIndex < 0) {
            insertIndex = 0;
        }
        else if (insertIndex > children.size()) {
            insertIndex = children.size();
        }

        // 如果被拖动的节点已经在目标节点下，需要先移除
        if (draggedItem.getParent() == targetItem) {
            children.remove(draggedItem);
            // 调整插入位置
            if (insertIndex > children.size()) {
                insertIndex = children.size();
            }
        }
        else {
            // 从原父节点移除
            TreeItem<T> oldParent = draggedItem.getParent();
            if (oldParent != null) {
                oldParent.getChildren().remove(draggedItem);
            }
        }

        // 插入到新位置
        children.add(insertIndex, draggedItem);

        // 刷新树视图
        treeView.refresh();

        logger.info("Successfully inserted item '{}' into '{}' at child position {}",
            draggedItem.getValue().getName(), targetItem.getValue().getName(), insertIndex);
    }

    */
/**
     * 创建拖动提示信息
     *//*

    private static <T extends BaseDataDto> String createDragTooltip(TreeItem<T> draggedItem, TreeItem<T> targetItem) {
        return createDragTooltip(draggedItem, targetItem, false);
    }

    */
/**
     * 创建拖动提示信息（带位置信息）
     *//*

    private static <T extends BaseDataDto> String createDragTooltip(TreeItem<T> draggedItem, TreeItem<T> targetItem, boolean insertAfter) {
        if (draggedItem == null || targetItem == null) {
            return "";
        }

        T draggedData = draggedItem.getValue();
        T targetData = targetItem.getValue();

        if (draggedItem.getParent() == targetItem.getParent()) {
            // 排序操作
            String position = insertAfter ? "之后" : "之前";
            return String.format("将 '%s' 移动到 '%s' %s", draggedData.getName(), targetData.getName(), position);
        }
        else {
            // 移动操作
            return String.format("将 '%s' 移动到 '%s' 下", draggedData.getName(), targetData.getName());
        }
    }

    */
/**
     * 创建拖动提示信息（支持子节点间插入）
     *//*

    private static <T extends BaseDataDto> String createDragTooltip(TreeItem<T> draggedItem, TreeItem<T> targetItem, InsertPosition insertPos) {
        if (draggedItem == null || targetItem == null || insertPos == null) {
            return "";
        }

        T draggedData = draggedItem.getValue();
        T targetData = targetItem.getValue();

        if (insertPos.isChildInsertion()) {
            // 子节点间插入
            if (targetItem.getChildren().size() > 0) {
                if (insertPos.childIndex == 0) {
                    return String.format("将 '%s' 插入到 '%s' 的第一个子节点位置", draggedData.getName(), targetData.getName());
                }
                else if (insertPos.childIndex >= targetItem.getChildren().size()) {
                    return String.format("将 '%s' 插入到 '%s' 的最后一个子节点位置", draggedData.getName(), targetData.getName());
                }
                else {
                    T childData = targetItem.getChildren().get(insertPos.childIndex - 1).getValue();
                    return String.format("将 '%s' 插入到 '%s' 的子节点 '%s' 之后", draggedData.getName(), targetData.getName(), childData.getName());
                }
            }
            else {
                return String.format("将 '%s' 移动到 '%s' 下", draggedData.getName(), targetData.getName());
            }
        }
        else if (draggedItem.getParent() == targetItem.getParent()) {
            // 排序操作
            String position = insertPos.insertAfter ? "之后" : "之前";
            return String.format("将 '%s' 移动到 '%s' %s", draggedData.getName(), targetData.getName(), position);
        }
        else {
            // 移动操作
            return String.format("将 '%s' 移动到 '%s' 下", draggedData.getName(), targetData.getName());
        }
    }

    */
/**
     * 判断是否插入到目标节点之后
     * 根据鼠标在节点中的位置判断：上半部分插入到之前，下半部分插入到之后
     *//*

    private static <T extends BaseDataDto> boolean isInsertAfter(DragEvent event, TreeItem<T> targetItem) {
        if (event == null || targetItem == null) {
            return false;
        }

        // 获取事件在目标节点中的Y坐标
        double eventY = event.getY();

        // 获取节点的高度（假设固定高度为30，与TreeView的fixedCellSize一致）
        double cellHeight = 30.0;

        // 如果鼠标在节点的下半部分，则插入到之后
        return eventY > cellHeight / 2;
    }

    */
/**
     * 获取更精确的插入位置信息
     *//*

    private static <T extends BaseDataDto> InsertPosition getInsertPosition(DragEvent event, TreeItem<T> targetItem) {
        if (event == null || targetItem == null) {
            return new InsertPosition(false, 0.0, -1);
        }

        double eventY = event.getY();
        double cellHeight = 30.0;
        double threshold = cellHeight * 0.3; // 使用30%作为阈值，更精确

        // 检查是否在子节点之间
        int childIndex = getChildInsertIndex(event, targetItem);
        if (childIndex >= 0) {
            // 在子节点之间插入
            return new InsertPosition(false, 0.0, childIndex);
        }

        boolean insertAfter = eventY > threshold;
        double positionRatio = eventY / cellHeight; // 位置比例，用于更精确的视觉反馈

        return new InsertPosition(insertAfter, positionRatio, -1);
    }

    */
/**
     * 检测是否在子节点之间插入，返回插入位置索引
     *//*

    private static <T extends BaseDataDto> int getChildInsertIndex(DragEvent event, TreeItem<T> targetItem) {
        if (targetItem == null || !targetItem.isExpanded() || targetItem.getChildren().isEmpty()) {
            return -1; // 没有子节点或未展开
        }

        double eventY = event.getY();
        double cellHeight = 30.0;

        // 更精确的子节点间插入检测
        // 在节点中间区域检测插入位置
        if (eventY > cellHeight * 0.2 && eventY < cellHeight * 0.8) {
            int childCount = targetItem.getChildren().size();

            if (childCount > 0) {
                // 计算每个子节点的插入区域
                double insertZoneHeight = cellHeight * 0.6; // 插入区域高度
                double insertZoneStart = cellHeight * 0.2; // 插入区域开始位置
                double relativeY = eventY - insertZoneStart;

                // 为每个子节点创建插入位置（包括第一个之前和最后一个之后）
                double insertSpacing = insertZoneHeight / (childCount + 1);

                // 检查是否在子节点之间
                for (int i = 0; i <= childCount; i++) {
                    double startY = i * insertSpacing;
                    double endY = (i + 1) * insertSpacing;

                    if (relativeY >= startY && relativeY <= endY) {
                        logger.debug("子节点间插入检测: eventY={}, relativeY={}, childIndex={}, childCount={}",
                            eventY, relativeY, i, childCount);
                        return i;
                    }
                }
            }
        }

        return -1;
    }

    */
/**
     * 插入位置信息类
     *//*

    private static class InsertPosition {
        final boolean insertAfter;
        final double positionRatio;
        final int childIndex; // -1表示不在子节点之间，>=0表示在子节点之间的插入位置

        InsertPosition(boolean insertAfter, double positionRatio, int childIndex) {
            this.insertAfter = insertAfter;
            this.positionRatio = positionRatio;
            this.childIndex = childIndex;
        }

        // 兼容性构造函数
        InsertPosition(boolean insertAfter, double positionRatio) {
            this(insertAfter, positionRatio, -1);
        }

        // 是否在子节点之间插入
        boolean isChildInsertion() {
            return childIndex >= 0;
        }
    }

    */
/**
     * 创建类似 Postman 的拖动指示线样式
     *//*

    private static String createDragIndicatorStyle(boolean insertAfter, boolean isValid) {
        return createDragIndicatorStyle(insertAfter, isValid, 1.0);
    }

    */
/**
     * 创建类似 Postman 的拖动指示线样式（带位置比例）
     *//*

    private static String createDragIndicatorStyle(boolean insertAfter, boolean isValid, double positionRatio) {
        if (!isValid) {
            return "-fx-background-color: rgba(255, 0, 0, 0.1); -fx-border-color: #ff4444; -fx-border-width: 2px; -fx-border-style: dashed;";
        }

        // 根据位置比例调整透明度
        double alpha = Math.max(0.1, Math.min(0.3, 0.1 + positionRatio * 0.2));
        String alphaStr = String.format("%.2f", alpha);

        if (insertAfter) {
            // 插入到之后：底部边框高亮，带渐变效果
            return String.format("-fx-background-color: rgba(0, 150, 0, %s); -fx-border-color: #00aa00; -fx-border-width: 0 0 3px 0; -fx-border-style: solid; -fx-border-insets: 0 0 0 0;", alphaStr);
        }
        else {
            // 插入到之前：顶部边框高亮，带渐变效果
            return String.format("-fx-background-color: rgba(0, 150, 0, %s); -fx-border-color: #00aa00; -fx-border-width: 3px 0 0 0; -fx-border-style: solid; -fx-border-insets: 0 0 0 0;", alphaStr);
        }
    }

    */
/**
     * 创建拖动时的节点样式（半透明效果）
     *//*

    private static String createDraggingNodeStyle() {
        return "-fx-background-color: rgba(100, 150, 255, 0.3); -fx-border-color: #6496ff; -fx-border-width: 1px; -fx-border-style: solid; -fx-opacity: 0.7; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 8, 0.5, 0, 2);";
    }

    */
/**
     * 创建高级拖动指示线样式（带阴影和动画效果）
     *//*

    private static String createAdvancedDragIndicatorStyle(boolean insertAfter, boolean isValid, double positionRatio) {
        return createAdvancedDragIndicatorStyle(insertAfter, isValid, positionRatio, -1);
    }

    */
/**
     * 创建高级拖动指示线样式（带阴影和动画效果，支持子节点间插入）
     *//*

    private static String createAdvancedDragIndicatorStyle(boolean insertAfter, boolean isValid, double positionRatio, int childIndex) {
        if (!isValid) {
            return "-fx-background-color: rgba(255, 0, 0, 0.1); -fx-border-color: #ff4444; -fx-border-width: 2px; -fx-border-style: dashed; -fx-effect: dropshadow(gaussian, rgba(255,0,0,0.3), 4, 0.3, 0, 1);";
        }

        // 如果在子节点之间插入
        if (childIndex >= 0) {
            return createChildInsertionStyle(childIndex, isValid);
        }

        // 根据位置比例调整透明度和颜色
        double alpha = Math.max(0.1, Math.min(0.4, 0.1 + positionRatio * 0.3));
        String alphaStr = String.format("%.2f", alpha);

        // 根据位置比例调整颜色强度
        int greenIntensity = (int) (100 + positionRatio * 100);
        String colorHex = String.format("#%02x%02x%02x", 0, greenIntensity, 0);

        if (insertAfter) {
            // 插入到之后：底部边框高亮，带阴影效果
            return String.format("-fx-background-color: rgba(0, %d, 0, %s); -fx-border-color: %s; -fx-border-width: 0 0 4px 0; -fx-border-style: solid; -fx-border-insets: 0 0 0 0; -fx-effect: dropshadow(gaussian, rgba(0,%d,0,0.4), 6, 0.4, 0, 2);",
                greenIntensity, alphaStr, colorHex, greenIntensity);
        }
        else {
            // 插入到之前：顶部边框高亮，带阴影效果
            return String.format("-fx-background-color: rgba(0, %d, 0, %s); -fx-border-color: %s; -fx-border-width: 4px 0 0 0; -fx-border-style: solid; -fx-border-insets: 0 0 0 0; -fx-effect: dropshadow(gaussian, rgba(0,%d,0,0.4), 6, 0.4, 0, -2);",
                greenIntensity, alphaStr, colorHex, greenIntensity);
        }
    }

    */
/**
     * 创建子节点间插入的样式
     *//*

    private static String createChildInsertionStyle(int childIndex, boolean isValid) {
        if (!isValid) {
            return "-fx-background-color: rgba(255, 0, 0, 0.1); -fx-border-color: #ff4444; -fx-border-width: 2px; -fx-border-style: dashed; -fx-effect: dropshadow(gaussian, rgba(255,0,0,0.3), 4, 0.3, 0, 1);";
        }

        // 子节点间插入：创建明显的插入线效果
        return "-fx-background-color: linear-gradient(to right, " +
            "transparent 0%, " +
            "rgba(0, 255, 0, 0.4) 20%, " +
            "rgba(0, 255, 0, 0.6) 40%, " +
            "rgba(0, 255, 0, 0.8) 50%, " +
            "rgba(0, 255, 0, 0.6) 60%, " +
            "rgba(0, 255, 0, 0.4) 80%, " +
            "transparent 100%); " +
            "-fx-border-color: #00ff00; " +
            "-fx-border-width: 0 0 0 4px; " +
            "-fx-border-style: solid; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,255,0,0.9), 15, 0.9, 0, 0); " +
            "-fx-padding: 0 0 0 4px; " +
            "-fx-background-radius: 0; " +
            "-fx-border-radius: 0;";
    }

    public static <T extends BaseDataDto> TreeView<T> buildTreeView(List<T> treeLevels, Node root, TreeOperateService<T> treeOperateService, Class<T> dataType)
        throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        T rootDataDto = dataType.getDeclaredConstructor().newInstance();
        rootDataDto.setId(0L);
        rootDataDto.setName(BaseI18N.getOrDefault("opencgl.base.tree.firstLevel.name"));
        rootDataDto.setIsLeaf(false);

        //增加增加根节点->超级父节点
        CustomizeTreeItem<T> rootTreeItem = new CustomizeTreeItem<>(rootDataDto);
        rootTreeItem.setExpanded(true);

        // 初始化TreeView
        TreeView<T> treeView = new TreeView<>(rootTreeItem);
        treeView.setFixedCellSize(30);
        treeView.setBorder(Border.EMPTY);
        // treeView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);


        buildTreeData(treeLevels, rootTreeItem);

        ContextMenu rootMenu = new ContextMenu();
        MenuItem addSecondDirMenuItem = new MenuItem(BaseI18N.getOrDefault("opencgl.base.tree.addSecondDirMenuItem"));//"添加子目录"
        MenuItem addSecondSubMenuItem = new MenuItem(BaseI18N.getOrDefault("opencgl.base.tree.addSecondSubMenuItem"));//"添加子节点"
        if (treeOperateService.supportImportAndExport()) {
            MenuItem importRootMenuItem = createMenuItem(treeOperateService, dataType, treeView, BaseI18N.getOrDefault("opencgl.base.tree.importMenuItem"), 0);
            MenuItem exportRootMenuItem = createMenuItem(treeOperateService, dataType, treeView, BaseI18N.getOrDefault("opencgl.base.tree.exportMenuItem"), 1);
            rootMenu.getItems().addAll(addSecondDirMenuItem, addSecondSubMenuItem, importRootMenuItem, exportRootMenuItem);
        }
        else {
            rootMenu.getItems().addAll(addSecondDirMenuItem, addSecondSubMenuItem);
        }

        // 目录菜单context
        ContextMenu dirMenu = new ContextMenu();
        MenuItem addSubDirMenuItem = new MenuItem(BaseI18N.getOrDefault("opencgl.base.tree.addSecondDirMenuItem"));// "添加子目录"
        MenuItem addSubMenuItem = new MenuItem(BaseI18N.getOrDefault("opencgl.base.tree.addSecondSubMenuItem"));//"添加子节点"
        MenuItem delCurrentDirMenuItem = new MenuItem(BaseI18N.getOrDefault("opencgl.base.tree.delCurrentDirMenuItem"));//"删除当前目录"
        MenuItem modDirectoryMenuItem = new MenuItem(BaseI18N.getOrDefault("opencgl.base.tree.modDirectoryMenuItem")); // "修改当前目录"
        if (treeOperateService.supportImportAndExport()) {
            MenuItem importDirMenuItem = createMenuItem(treeOperateService, dataType, treeView, BaseI18N.getOrDefault("opencgl.base.tree.importMenuItem"), 0);
            MenuItem exportDirMenuItem = createMenuItem(treeOperateService, dataType, treeView, BaseI18N.getOrDefault("opencgl.base.tree.exportMenuItem"), 1);
            dirMenu.getItems().addAll(addSubDirMenuItem, addSubMenuItem, delCurrentDirMenuItem, modDirectoryMenuItem, importDirMenuItem, exportDirMenuItem);
        }
        else {
            dirMenu.getItems().addAll(addSubDirMenuItem, addSubMenuItem, delCurrentDirMenuItem, modDirectoryMenuItem);
        }


        // 子节点context
        ContextMenu subMenu = new ContextMenu();
        MenuItem modSubMenuItem = new MenuItem(BaseI18N.getOrDefault("opencgl.base.tree.modSubMenuItem"));//"修改当前节点"
        MenuItem delCurrentSubMenuItem = new MenuItem(BaseI18N.getOrDefault("opencgl.base.tree.delCurrentSubMenuItem")); //"删除当前节点"

        subMenu.getItems().addAll(modSubMenuItem, delCurrentSubMenuItem);

        if (treeOperateService.supportImportAndExport()) {
            MenuItem exportSubMenuItem = createMenuItem(treeOperateService, dataType, treeView, BaseI18N.getOrDefault("opencgl.base.tree.exportMenuItem"), 1);
            subMenu.getItems().add(exportSubMenuItem);
        }
        else {
            subMenu.getItems().addAll(modSubMenuItem, delCurrentSubMenuItem);
        }

        // 设置节点取对象name
        treeView.setCellFactory(new Callback<>() {
            public TreeCell<T> call(TreeView<T> tv) {
                return new TreeCell<>() {
                    private TreeItem<T> draggedItem;
                    private TreeItem<T> targetItem;

                    @Override
                    protected void updateItem(T item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) {
                            setText("");
                            setGraphic(null);
                            setContextMenu(null);
                            return;
                        }
                        setText(item.getName());
                        if (item.getId() == null || item.getId() == 0) {
                            setContextMenu(rootMenu);
                        }
                        else if (item.getIsLeaf()) {
                            setContextMenu(subMenu);
                        }
                        else {
                            setContextMenu(dirMenu);
                        }
                    }

                    @Override
                    public void updateSelected(boolean selected) {
                        super.updateSelected(selected);
                        if (selected) {
                            TreeItem<T> item = getTreeItem();
                            if (item != null && !item.isExpanded()) {
                                recursivelySetExpanded(item, false);
                            }
                        }
                    }


                    {
                        // 拖动开始事件
                        setOnDragDetected((MouseEvent event) -> {
                            if (getItem() == null) {
                                return;
                            }

                            draggedItem = getTreeItem();
                            if (draggedItem == null || draggedItem.getValue().getId() == 0) {
                                return; // 不能拖动根节点
                            }

                            // 设置拖动时的样式
                            setStyle(createDraggingNodeStyle());

                            Dragboard dragboard = startDragAndDrop(TransferMode.MOVE);
                            ClipboardContent content = new ClipboardContent();
                            // content.put(DRAG_DATA_FORMAT, draggedItem.getValue().getId().toString());
                            dragboard.setContent(content);

                            event.consume();
                        });

                        // 拖动进入事件
                        setOnDragEntered((DragEvent event) -> {
//                            if (event.getDragboard().hasContent(DRAG_DATA_FORMAT)) {
//                                targetItem = getTreeItem();
//                                if (targetItem != null) {
//                                    boolean isValid = isValidDragOperation(draggedItem, targetItem);
//                                    InsertPosition insertPos = getInsertPosition(event, targetItem);
//
//                                    // 使用高级样式系统，带位置比例和阴影效果，支持子节点间插入
//                                    setStyle(createAdvancedDragIndicatorStyle(insertPos.insertAfter, isValid, insertPos.positionRatio, insertPos.childIndex));
//
//                                    if (isValid) {
//                                        event.acceptTransferModes(TransferMode.MOVE);
//
//                                        // 显示拖动提示
//                                        String tooltipText = createDragTooltip(draggedItem, targetItem, insertPos);
//                                        if (!tooltipText.isEmpty()) {
//                                            Tooltip.install(this, new Tooltip(tooltipText));
//                                        }
//                                    } else {
//                                        // 显示错误提示
//                                        String errorText = "不允许的拖动操作";
//                                        if (targetItem != null && draggedItem != null) {
//                                            T draggedData = draggedItem.getValue();
//                                            T targetData = targetItem.getValue();
//                                            if (draggedData.getIsLeaf() && targetData.getIsLeaf()) {
//                                                errorText = "非目录节点不能拖动到非目录节点下";
//                                            } else if (!draggedData.getIsLeaf() && targetData.getIsLeaf()) {
//                                                errorText = "目录节点不能拖动到非目录节点下";
//                                            }
//                                        }
//                                        Tooltip.install(this, new Tooltip(errorText));
//                                    }
//                                }
//                            }
                        });

                        // 拖动悬停事件
                        setOnDragOver((DragEvent event) -> {
//                            if (event.getDragboard().hasContent(DRAG_DATA_FORMAT)) {
//                                targetItem = getTreeItem();
//                                if (targetItem != null) {
//                                    boolean isValid = isValidDragOperation(draggedItem, targetItem);
//                                    InsertPosition insertPos = getInsertPosition(event, targetItem);
//
//                                    // 动态更新样式，带位置比例和阴影效果，支持子节点间插入
//                                    setStyle(createAdvancedDragIndicatorStyle(insertPos.insertAfter, isValid, insertPos.positionRatio, insertPos.childIndex));
//
//                                    if (isValid) {
//                                        // 更新提示信息
//                                        String tooltipText = createDragTooltip(draggedItem, targetItem, insertPos);
//                                        if (!tooltipText.isEmpty()) {
//                                            Tooltip.install(this, new Tooltip(tooltipText));
//                                        }
//
//                                        event.acceptTransferModes(TransferMode.MOVE);
//                                    }
//                                }
//                            }
                        });

                        // 拖动离开事件
                        setOnDragExited((DragEvent event) -> {
                            setStyle("");
                            Tooltip.uninstall(this, null);
                        });

                        // 拖动放下事件
                        setOnDragDropped((DragEvent event) -> {
//                            if (event.getDragboard().hasContent(DRAG_DATA_FORMAT)) {
//                                targetItem = getTreeItem();
//                                if (targetItem != null && draggedItem != null) {
//                                    InsertPosition insertPos = getInsertPosition(event, targetItem);
//
//                                    if (insertPos.isChildInsertion()) {
//                                        // 子节点间插入
//                                        performChildInsertion(tv, draggedItem, targetItem, treeOperateService, insertPos.childIndex);
//                                    } else if (draggedItem.getParent() == targetItem.getParent()) {
//                                        // 同一父节点下的排序操作
//                                        performSortOperation(tv, draggedItem, targetItem, treeOperateService, insertPos.insertAfter);
//                                    } else {
//                                        // 跨父节点移动
//                                        performDragOperation(tv, draggedItem, targetItem, treeOperateService);
//                                    }
//                                    event.setDropCompleted(true);
//                                } else {
//                                    event.setDropCompleted(false);
//                                }
//                            } else {
//                                event.setDropCompleted(false);
//                            }

                            // 恢复原始样式
                            setStyle("");
                            Tooltip.uninstall(this, null);

                            // 恢复被拖动节点的原始样式
                            if (draggedItem != null) {
                                // 这里可以添加恢复被拖动节点样式的逻辑
                            }
                        });
                    }
                };
            }
        });

        // 添加拖动完成事件处理器
        treeView.setOnDragDone((DragEvent event) -> {
            // 拖动完成，清理状态
            // 这里可以添加全局状态清理的逻辑
        });

        treeView.setOnKeyPressed(keyEvent -> {
            treeView.setContextMenu(null);
            if (null == treeView.getSelectionModel().getSelectedItem()) {
                return;
            }
            if (treeView.getSelectionModel().getSelectedItem().getValue().getIsLeaf()) {
                treeOperateService.changeToDisplay(treeView.getSelectionModel().getSelectedItem().getValue());
            }
        });

        treeView.setOnMouseClicked(mouseEvent -> {
            if (null == treeView.getSelectionModel().getSelectedItem()) {
                return;
            }
            if (treeView.getSelectionModel().getSelectedItem().getValue().getIsLeaf()) {
                treeOperateService.changeToDisplay(treeView.getSelectionModel().getSelectedItem().getValue());
            }
        });

        //根节点添加事件
        addSecondDirMenuItem.setOnAction(actionEvent -> addTreeData(treeOperateService, dataType, treeView, false));

        addSecondSubMenuItem.setOnAction(actionEvent -> addTreeData(treeOperateService, dataType, treeView, true));

        //目录节点事件
        addSubDirMenuItem.setOnAction(actionEvent -> addTreeData(treeOperateService, dataType, treeView, false));

        addSubMenuItem.setOnAction(actionEvent -> addTreeData(treeOperateService, dataType, treeView, true));

        // 删除当前目录节点事件，包含所有其子节点的删除内容
        delCurrentDirMenuItem.setOnAction(actionEvent -> {
            Boolean delete = DialogUtil.deleteConfirm();
            if (delete) {
                TreeItem<T> selectedItem = treeView.getSelectionModel().getSelectedItem();
                deleteTreeItem(treeView, selectedItem, treeOperateService);
            }

        });

        // 删除当前内容节点事件
        delCurrentSubMenuItem.setOnAction(actionEvent -> {
            Boolean deleteResult = DialogUtil.deleteConfirm();
            if (deleteResult) {
                TreeItem<T> selectedTreeItem = treeView.getSelectionModel().getSelectedItem();
                CustomizeTreeItem<T> delete = treeOperateService.delete(selectedTreeItem.getValue());
                if (delete != null) {
                    treeView.getSelectionModel().getSelectedItem().getParent().getChildren().remove(selectedTreeItem);
                }
            }

        });

        modDirectoryMenuItem.setOnAction(actionEvent -> updateTreeMenuItem(treeOperateService, dataType, treeView));

        //叶子节点事件
        modSubMenuItem.setOnAction(actionEvent -> updateTreeMenuItem(treeOperateService, dataType, treeView));

        return treeView;
    }

    private static <T extends BaseDataDto> void importAndFormatData(TreeOperateService<T> treeOperateService, List<T> treeLevels, TreeView<T> treeView) {
        // 构造所有树
        TreeMap<Long, CustomizeTreeItem<T>> treeItemMap = new TreeMap<>();
        // 原父子关系映射
        Map<Long, Long> treeOldAndNewParentMapping = new HashMap<>();
        // 按照 id 排序
        List<T> sortedTreeLevels = new ArrayList<>(
            treeLevels.stream().sorted(Comparator.comparing(BaseDataDto::getId)).toList());
        for (T baseLevelDto : sortedTreeLevels) {
            treeItemMap.put(baseLevelDto.getId(), new CustomizeTreeItem<>(baseLevelDto));
            // 原 id 的父子关系
            treeOldAndNewParentMapping.put(baseLevelDto.getId(), baseLevelDto.getParentId());
        }
        TreeItem<T> selectedItem = treeView.getSelectionModel().getSelectedItem();
        if (MapUtils.isNotEmpty(treeItemMap)) {
            List<T> gonnaImportTreeItemList = new ArrayList<>(16);
            for (T baseLevelDto : sortedTreeLevels) {
                logger.info("begin deal data {}", baseLevelDto);
                CustomizeTreeItem<T> baseDataDtoTreeItem = treeItemMap.get(baseLevelDto.getId());
                T value = baseDataDtoTreeItem.getValue();
                Long originParentId = value.getParentId();
                Long originNodeId = value.getId();
                Long newParentId = selectedItem.getValue().getId();
                value.setParentId(newParentId);
                if (originParentId != null && originParentId != 0) {
                    Long parentId =
                        Optional.ofNullable(treeOldAndNewParentMapping.get(value.getId()))
                            .orElse(newParentId);
                    value.setParentId(parentId);
                }
                CustomizeTreeItem<T> add = treeOperateService.importData(value);
                gonnaImportTreeItemList.add(add.getValue());
                treeOldAndNewParentMapping.forEach((aLong, aLong2) -> {
                    if (originNodeId.equals(aLong2)) {
                        treeOldAndNewParentMapping.put(aLong, add.getValue().getId());
                    }
                });
            }
            CustomizeTreeItem<T> customizeTreeItem = (CustomizeTreeItem<T>) treeView.getSelectionModel().getSelectedItem();
            buildTreeData(gonnaImportTreeItemList, customizeTreeItem);
        }
    }

    private static <T extends BaseDataDto> void buildTreeData(List<T> treeLevels, CustomizeTreeItem<T> rootTreeItem) {
        // 构造所有树
        TreeMap<Long, CustomizeTreeItem<T>> treeItemMap = new TreeMap<>();
        for (T baseLevelDto : treeLevels) {
            treeItemMap.put(baseLevelDto.getId(), new CustomizeTreeItem<>(baseLevelDto));
        }
        if (MapUtils.isNotEmpty(treeItemMap)) {
            treeItemMap.forEach((integer, baseDataDtoTreeItem) -> {
                T value = baseDataDtoTreeItem.getValue();
                if (value.getParentId() == null || value.getParentId() == 0) {
                    rootTreeItem.getChildren().add(baseDataDtoTreeItem);
                }
                else {
                    CustomizeTreeItem<T> fatherTreeItem = treeItemMap.get(value.getParentId());
                    Objects.requireNonNullElse(fatherTreeItem, rootTreeItem).getChildren().add(baseDataDtoTreeItem);
                }
            });
        }
    }

    private static <T extends BaseDataDto> void updateTreeMenuItem(TreeOperateService<T> treeOperateService, Class<T> dataType, TreeView<T> treeView) {
        T selectedTreeItem = treeView.getSelectionModel().getSelectedItem().getValue();
        String newTreeName = DialogUtil.show(selectedTreeItem.getName());
        if (newTreeName.isEmpty()) {
            return;
        }
        selectedTreeItem.setName(newTreeName);
        CustomizeTreeItem<T> update = treeOperateService.update(selectedTreeItem);
        treeView.getSelectionModel().getSelectedItem().setValue(update.getValue());
        treeView.refresh();
    }

    private static <T extends BaseDataDto> void addTreeData(TreeOperateService<T> treeOperateService, Class<T> dataType, TreeView<T> treeView, Boolean isLeaf) {
        T selectedTreeItem = treeView.getSelectionModel().getSelectedItem().getValue();
        String newTreeName = DialogUtil.show();
        if (newTreeName.isEmpty()) {
            return;
        }
        try {
            T newTreeData = dataType.getDeclaredConstructor().newInstance();
            newTreeData.setParentId(selectedTreeItem.getId());
            newTreeData.setName(newTreeName);
            newTreeData.setIsLeaf(isLeaf);
            CustomizeTreeItem<T> add = treeOperateService.add(newTreeData);
            treeView.getSelectionModel().getSelectedItem().getChildren().add(add);
            treeView.getSelectionModel().select(add);
            if (!add.isLeaf() || !add.getParent().isExpanded()) {
                add.setExpanded(true);
                add.getParent().setExpanded(true);
            }
        }
        catch (InstantiationException | IllegalAccessException | InvocationTargetException |
               NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }


    private static <T extends BaseDataDto> MenuItem createMenuItem(TreeOperateService<T> treeOperateService, Class<T> dataType, TreeView<T> treeView, String text, Integer mode) {
        MenuItem menuItem = new MenuItem(text);
        if (mode == 0) {
            // 添加菜单项的事件处理逻辑
            menuItem.setOnAction(event -> importTreeData(treeOperateService, dataType, treeView));
        }
        else if (mode == 1) {
            // 添加菜单项的事件处理逻辑
            menuItem.setOnAction(event -> exportTreeData(treeOperateService, dataType, treeView));
        }
        return menuItem;
    }

    //todo
    private static <T extends BaseDataDto> void importTreeData(TreeOperateService<T> treeOperateService, Class<T> dataType, TreeView<T> treeView) {
        T selectedTreeItem = treeView.getSelectionModel().getSelectedItem().getValue();
        String filePath = DialogUtil.showImportDialog();
        if (!PathValidatorUtil.isValidPath(filePath)) {
            return;
        }
        try {
            File file = new File(filePath);
            String content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            List<T> importTreeLevels = JSON.parseArray(content, dataType);
            treeOperateService.validateImportData(importTreeLevels);
            importAndFormatData(treeOperateService, importTreeLevels, treeView);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T extends BaseDataDto> void exportTreeData(TreeOperateService<T> treeOperateService, Class<T> dataType, TreeView<T> treeView) {
        TreeItem<T> selectedTreeItem = treeView.getSelectionModel().getSelectedItem();
        String filePath = DialogUtil.showExportDialog();
        if (!PathValidatorUtil.isValidPath(filePath)) {
            return;
        }
        File exportPath = new File(filePath);
        try {
            List<T> treeData = new ArrayList<>(16);
            T selectedTreeItemData = selectedTreeItem.getValue();
            selectedTreeItemData.setParentId(null);
            if (selectedTreeItemData.getIsLeaf()) {
                treeData.add(selectedTreeItemData);
            }
            else {
                treeData.add(selectedTreeItemData);
                getTreeItemDataList(treeData, selectedTreeItem);
            }
            String newFileName = selectedTreeItemData.getName() + "_export_" + DateUtils.format(new Date()) + ".json";
            File writeFile = new File(filePath + File.separator + newFileName);
            FileUtils.write(writeFile, JSON.toJSONString(treeData, SerializerFeature.PrettyFormat, SerializerFeature.DisableCircularReferenceDetect), StandardCharsets.UTF_8);
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setHeaderText("");
            alert.setContentText(BaseI18N.getOrDefault("opencgl.base.export.success.open.question"));
            alert.showAndWait();
            if (ControlResources.getString("Dialog.ok.button")
                .equals(alert.getResult().getText())) {
                Desktop.getDesktop().open(writeFile);
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T extends BaseDataDto> void deleteTreeItem(TreeView<T> treeView, TreeItem<T> selectedItem, TreeOperateService<T> treeOperateService) {
        if (CollectionUtils.isNotEmpty(selectedItem.getChildren())) {
            selectedItem.getChildren().forEach(item -> deleteTreeItem(treeView, item, treeOperateService));
        }
        CustomizeTreeItem<T> delete = treeOperateService.delete(selectedItem.getValue());
        if (delete != null) {
            treeView.getSelectionModel().getSelectedItem().getParent().getChildren().remove(selectedItem);
        }
    }


    private static <T extends BaseDataDto> void recursivelySetExpanded(TreeItem<T> item, boolean expanded) {
        item.setExpanded(expanded);
        item.getChildren().forEach(child -> recursivelySetExpanded(child, expanded));
    }

    private static <T extends BaseDataDto> void getTreeItemDataList(List<T> treeData, TreeItem<T> treeItem) {
        if (CollectionUtils.isNotEmpty(treeItem.getChildren())) {
            treeItem.getChildren().forEach(item -> {
                treeData.add(item.getValue());
                if (!item.isLeaf()) {
                    getTreeItemDataList(treeData, item);
                }
            });
        }
    }

    */
/**
     * // 弹框增加和修改
     * private static <T extends BaseDataDto> void alertOperateTitle(Node node, String nodeName, TreeView<T> jfxTreeView, Function<String, CustomizeTreeItem<T>> function) {
     * String text = DialogUtil.show(nodeName);
     * CustomizeTreeItem<T> CustomizeTreeItem = function.apply(text);
     * }
     *//*

}
*/
