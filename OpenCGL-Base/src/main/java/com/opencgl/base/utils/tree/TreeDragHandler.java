package com.opencgl.base.utils.tree;

import com.opencgl.base.model.BaseDataDto;
import com.opencgl.base.service.TreeOperateService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Bounds;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.util.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * 树形拖拽处理器
 * 负责处理TreeView的拖拽排序和移动功能
 * 支持拖拽到边缘时自动滚动
 *
 * @author Chance.W
 */
public class TreeDragHandler<T extends BaseDataDto> {

    private static final Logger logger = LoggerFactory.getLogger(TreeDragHandler.class);

    private static final double CELL_HEIGHT = 30.0;
    private static final double DROP_ZONE_RATIO = 0.25; // 上下25%区域为排序区
    private static final double SCROLL_EDGE_SIZE = 40.0; // 距离边缘多少像素触发滚动
    private static final int SCROLL_INTERVAL_MS = 50; // 滚动检查间隔

    private final TreeView<T> treeView;
    private final TreeOperateService<T> service;
    private List<TreeItem<T>> draggedItems = new ArrayList<>();  // 支持多选拖拽

    // 自动滚动相关
    private Timeline autoScrollTimeline;
    private int scrollDirection = 0; // -1: up, 0: stop, 1: down
    private int scrollTargetIndex = 0; // 当前滚动目标索引

    public TreeDragHandler(TreeView<T> treeView, TreeOperateService<T> service) {
        this.treeView = treeView;
        this.service = service;
        initAutoScrollTimeline();
    }

    private void initAutoScrollTimeline() {
        autoScrollTimeline = new Timeline(new KeyFrame(Duration.millis(SCROLL_INTERVAL_MS), e -> {
            if (scrollDirection == 0) return;

            int expandedItemCount = treeView.getExpandedItemCount();
            if (expandedItemCount == 0) return;

            // 根据方向递增/递减目标索引
            scrollTargetIndex += scrollDirection;

            // 边界检查
            scrollTargetIndex = Math.max(0, Math.min(scrollTargetIndex, expandedItemCount - 1));

            // 执行滚动
            treeView.scrollTo(scrollTargetIndex);
        }));
        autoScrollTimeline.setCycleCount(Timeline.INDEFINITE);
    }

    private void startAutoScroll(int direction) {
        if (scrollDirection != direction) {
            scrollDirection = direction;
            // 初始化滚动目标索引
            if (direction != 0) {
                int expandedItemCount = treeView.getExpandedItemCount();
                int visibleCount = (int) (treeView.getHeight() / CELL_HEIGHT);

                // 如果之前没有有效的索引，根据方向设置一个合理的起始点
                if (scrollTargetIndex <= 0 || scrollTargetIndex >= expandedItemCount) {
                    if (direction < 0) {
                        // 向上滚动，从大约可见区域顶部附近开始（假设在中间位置）
                        scrollTargetIndex = Math.max(visibleCount, expandedItemCount / 2);
                    }
                    else {
                        // 向下滚动，从大约可见区域底部附近开始
                        scrollTargetIndex = Math.min(expandedItemCount - visibleCount, expandedItemCount / 2);
                    }
                }
            }
            if (direction != 0 && autoScrollTimeline.getStatus() != Timeline.Status.RUNNING) {
                autoScrollTimeline.play();
            }
        }
    }

    private void stopAutoScroll() {
        scrollDirection = 0;
        autoScrollTimeline.stop();
    }

    /**
     * 为TreeCell设置拖拽事件
     */
    public void setupDragEvents(TreeCell<T> cell) {
        // 拖拽开始
        cell.setOnDragDetected(event -> handleDragDetected(cell, event));

        // 拖拽进入
        cell.setOnDragEntered(event -> handleDragEntered(cell, event));

        // 拖拽悬停
        cell.setOnDragOver(event -> handleDragOver(cell, event));

        // 拖拽离开
        cell.setOnDragExited(event -> handleDragExited(cell, event));

        // 拖拽放下
        cell.setOnDragDropped(event -> handleDragDropped(cell, event));

        // 拖拽完成
        cell.setOnDragDone(event -> handleDragDone(cell, event));
    }

    private void handleDragDetected(TreeCell<T> cell, MouseEvent event) {
        if (cell.getItem() == null) return;

        TreeItem<T> clickedItem = cell.getTreeItem();
        if (clickedItem == null) return;

        // 不允许拖拽根节点
        BaseDataDto data = clickedItem.getValue();
        if (data.getId() == null || data.getId() == 0) {
            return;
        }

        // 收集所有选中的项（支持多选）
        draggedItems = treeView.getSelectionModel().getSelectedItems().stream()
            .filter(item -> item != null && item.getValue() != null)
            .filter(item -> item.getValue().getId() != null && item.getValue().getId() != 0)
            .collect(Collectors.toCollection(ArrayList::new));
        
        // 如果点击的项不在选中列表中，只拖拽点击的项
        if (!draggedItems.contains(clickedItem)) {
            draggedItems.clear();
            draggedItems.add(clickedItem);
        }
        
        if (draggedItems.isEmpty()) return;

        // 设置拖拽样式（为所有选中的单元格）
        cell.setStyle(createDraggingStyle());

        // 启动拖拽
        Dragboard db = cell.startDragAndDrop(TransferMode.MOVE);
        ClipboardContent content = new ClipboardContent();
        // 存储所有拖拽项的ID
        String ids = draggedItems.stream()
            .map(item -> item.getValue().getId().toString())
            .collect(Collectors.joining(","));
        content.putString(ids);
        db.setContent(content);

        logger.debug("开始拖拽 {} 个节点", draggedItems.size());
        event.consume();
    }


    private void handleDragEntered(TreeCell<T> cell, DragEvent event) {
        if (draggedItems.isEmpty() || cell.getItem() == null) return;

        TreeItem<T> targetItem = cell.getTreeItem();
        if (targetItem == null || draggedItems.contains(targetItem)) return;

        DropPosition pos = calculateDropPosition(event, cell);
        boolean valid = isValidDropForAll(draggedItems, targetItem, pos);

        cell.setStyle(createDropIndicatorStyle(pos, valid));

        if (valid) {
            String tooltip = draggedItems.size() == 1 
                ? createDropTooltip(draggedItems.get(0), targetItem, pos)
                : String.format("移动 %d 个节点", draggedItems.size());
            Tooltip.install(cell, new Tooltip(tooltip));
        }

        event.consume();
    }

    private void handleDragOver(TreeCell<T> cell, DragEvent event) {
        if (draggedItems.isEmpty() || cell.getItem() == null) return;

        TreeItem<T> targetItem = cell.getTreeItem();
        if (targetItem == null || draggedItems.contains(targetItem)) return;

        DropPosition pos = calculateDropPosition(event, cell);
        boolean valid = isValidDropForAll(draggedItems, targetItem, pos);

        // 动态更新样式
        cell.setStyle(createDropIndicatorStyle(pos, valid));

        if (valid) {
            event.acceptTransferModes(TransferMode.MOVE);
        }

        // 自动滚动逻辑：检测鼠标是否靠近 TreeView 边缘
        Bounds tvBounds = treeView.localToScene(treeView.getBoundsInLocal());
        double sceneY = event.getSceneY();
        double topEdge = tvBounds.getMinY();
        double bottomEdge = tvBounds.getMaxY();

        if (sceneY < topEdge + SCROLL_EDGE_SIZE) {
            startAutoScroll(-1); // 向上滚动
        }
        else if (sceneY > bottomEdge - SCROLL_EDGE_SIZE) {
            startAutoScroll(1); // 向下滚动
        }
        else {
            stopAutoScroll();
        }

        event.consume();
    }

    private void handleDragExited(TreeCell<T> cell, DragEvent event) {
        cell.setStyle("");
        Tooltip.uninstall(cell, null);
        // 不在这里停止滚动，因为可能只是在 cell 之间移动
        event.consume();
    }

    private void handleDragDropped(TreeCell<T> cell, DragEvent event) {
        if (draggedItems.isEmpty() || cell.getItem() == null) {
            event.setDropCompleted(false);
            return;
        }

        TreeItem<T> targetItem = cell.getTreeItem();
        if (targetItem == null) {
            event.setDropCompleted(false);
            return;
        }

        DropPosition pos = calculateDropPosition(event, cell);

        if (isValidDropForAll(draggedItems, targetItem, pos)) {
            performDropForAll(draggedItems, targetItem, pos);
            event.setDropCompleted(true);
            logger.info("拖拽完成: {} 个节点 -> {} ({})",
                draggedItems.size(),
                targetItem.getValue().getName(),
                pos);
        }
        else {
            event.setDropCompleted(false);
        }

        cell.setStyle("");
        event.consume();
    }

    private void handleDragDone(TreeCell<T> cell, DragEvent event) {
        cell.setStyle("");
        // 清除选中状态，避免选中停留在其他节点
        treeView.getSelectionModel().clearSelection();
        draggedItems.clear();
        stopAutoScroll(); // 拖拽结束，停止自动滚动
        event.consume();
    }

    /**
     * 计算放置位置
     */
    private DropPosition calculateDropPosition(DragEvent event, TreeCell<T> cell) {
        double y = event.getY();
        double height = cell.getHeight() > 0 ? cell.getHeight() : CELL_HEIGHT;

        if (y < height * DROP_ZONE_RATIO) {
            return DropPosition.BEFORE;
        }
        else if (y > height * (1 - DROP_ZONE_RATIO)) {
            return DropPosition.AFTER;
        }
        else {
            return DropPosition.INTO;
        }
    }

    /**
     * 验证放置是否有效
     */
    public boolean isValidDrop(TreeItem<T> dragged, TreeItem<T> target, DropPosition pos) {
        if (dragged == null || target == null || dragged == target) {
            return false;
        }

        BaseDataDto draggedData = dragged.getValue();
        BaseDataDto targetData = target.getValue();

        // 不能放到自己的后代节点
        if (isDescendant(dragged, target)) {
            return false;
        }

        switch (pos) {
            case INTO:
                // 只能放入目录节点
                return !Boolean.TRUE.equals(targetData.getIsLeaf());

            case BEFORE:
            case AFTER:
                // 同级排序：叶子节点只能和叶子节点排序，目录只能和目录排序
                // 或者目标有父节点（避免在根节点旁边排序）
                TreeItem<T> targetParent = target.getParent();
                if (targetParent == null) return false;

                // 如果是跨父节点排序，目标父节点必须是目录
                TreeItem<T> draggedParent = dragged.getParent();
                if (draggedParent != targetParent) {
                    BaseDataDto parentData = targetParent.getValue();
                    return !Boolean.TRUE.equals(parentData.getIsLeaf());
                }
                return true;

            default:
                return false;
        }
    }

    /**
     * 验证所有拖拽项的放置是否有效
     */
    public boolean isValidDropForAll(List<TreeItem<T>> draggedList, TreeItem<T> target, DropPosition pos) {
        if (draggedList == null || draggedList.isEmpty() || target == null) {
            return false;
        }
        
        // 目标不能是任何拖拽项本身
        if (draggedList.contains(target)) {
            return false;
        }
        
        // 目标不能是任何拖拽项的后代
        for (TreeItem<T> dragged : draggedList) {
            if (isDescendant(dragged, target)) {
                return false;
            }
        }
        
        // 验证每个拖拽项
        for (TreeItem<T> dragged : draggedList) {
            if (!isValidDrop(dragged, target, pos)) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * 执行放置操作
     */
    public void performDrop(TreeItem<T> dragged, TreeItem<T> target, DropPosition pos) {
        TreeItem<T> oldParent = dragged.getParent();
        TreeItem<T> newParent;
        int insertIndex;

        switch (pos) {
            case BEFORE:
                newParent = target.getParent();
                insertIndex = newParent.getChildren().indexOf(target);
                break;
            case INTO:
                newParent = target;
                insertIndex = target.getChildren().size();
                target.setExpanded(true);
                break;
            case AFTER:
                newParent = target.getParent();
                insertIndex = newParent.getChildren().indexOf(target) + 1;
                break;
            default:
                return;
        }

        // 1. 从旧位置移除
        int oldIndex = oldParent.getChildren().indexOf(dragged);
        oldParent.getChildren().remove(dragged);

        // 2. 调整插入位置（如果在同一父节点且旧位置在新位置之前）
        if (oldParent == newParent && oldIndex < insertIndex) {
            insertIndex--;
        }

        // 3. 确保索引有效
        insertIndex = Math.max(0, Math.min(insertIndex, newParent.getChildren().size()));

        // 4. 插入新位置
        newParent.getChildren().add(insertIndex, dragged);

        // 5. 更新数据
        BaseDataDto data = dragged.getValue();
        Long oldParentId = data.getParentId();
        BaseDataDto newParentData = newParent.getValue();
        Long newParentId = newParentData.getId();

        if (!oldParentId.equals(newParentId)) {
            @SuppressWarnings("unchecked")
            T typedData = (T) data;
            service.move(typedData, newParentId);
        }

        // 6. 批量更新新父节点下所有子节点的排序
        List<T> newSiblings = newParent.getChildren().stream()
            .map(TreeItem::getValue)
            .collect(Collectors.toList());
        service.batchReorder(newSiblings);

        // 7. 如果跨父节点，也更新旧父节点的排序
        if (oldParent != newParent) {
            List<T> oldSiblings = oldParent.getChildren().stream()
                .map(TreeItem::getValue)
                .collect(Collectors.toList());
            service.batchReorder(oldSiblings);
        }

        // 8. 刷新树
        treeView.refresh();
    }

    /**
     * 批量执行放置操作（支持多选拖拽）
     */
    public void performDropForAll(List<TreeItem<T>> draggedList, TreeItem<T> target, DropPosition pos) {
        if (draggedList == null || draggedList.isEmpty()) return;
        
        // 收集所有受影响的父节点，用于后续批量更新排序
        Set<TreeItem<T>> affectedParents = new HashSet<>();
        
        // 确定新父节点和初始插入位置
        TreeItem<T> newParent;
        int baseInsertIndex;
        
        switch (pos) {
            case BEFORE:
                newParent = target.getParent();
                baseInsertIndex = newParent.getChildren().indexOf(target);
                break;
            case INTO:
                newParent = target;
                baseInsertIndex = target.getChildren().size();
                target.setExpanded(true);
                break;
            case AFTER:
                newParent = target.getParent();
                baseInsertIndex = newParent.getChildren().indexOf(target) + 1;
                break;
            default:
                return;
        }
        
        affectedParents.add(newParent);
        
        // 先从旧位置移除所有拖拽项（从后向前遍历避免索引问题）
        for (TreeItem<T> dragged : draggedList) {
            TreeItem<T> oldParent = dragged.getParent();
            if (oldParent != null) {
                affectedParents.add(oldParent);
                oldParent.getChildren().remove(dragged);
            }
        }
        
        // 重新计算插入位置（移除后索引可能变化）
        if (pos == DropPosition.BEFORE) {
            baseInsertIndex = newParent.getChildren().indexOf(target);
            if (baseInsertIndex < 0) baseInsertIndex = 0;
        } else if (pos == DropPosition.AFTER) {
            baseInsertIndex = newParent.getChildren().indexOf(target) + 1;
            if (baseInsertIndex < 1) baseInsertIndex = newParent.getChildren().size();
        }
        
        // 在新位置依次插入所有拖拽项
        int insertOffset = 0;
        for (TreeItem<T> dragged : draggedList) {
            int actualIndex = Math.min(baseInsertIndex + insertOffset, newParent.getChildren().size());
            newParent.getChildren().add(actualIndex, dragged);
            insertOffset++;
            
            // 更新数据层的 parentId
            BaseDataDto data = dragged.getValue();
            Long oldParentId = data.getParentId();
            Long newParentId = newParent.getValue().getId();
            
            if (!newParentId.equals(oldParentId)) {
                @SuppressWarnings("unchecked")
                T typedData = (T) data;
                service.move(typedData, newParentId);
            }
        }
        
        // 批量更新所有受影响父节点的子节点排序
        for (TreeItem<T> parent : affectedParents) {
            List<T> siblings = parent.getChildren().stream()
                .map(TreeItem::getValue)
                .collect(Collectors.toList());
            service.batchReorder(siblings);
        }
        
        // 刷新树
        treeView.refresh();
    }

    /**
     * 检查target是否是dragged的后代
     */
    private boolean isDescendant(TreeItem<T> dragged, TreeItem<T> target) {
        TreeItem<T> parent = target.getParent();
        while (parent != null) {
            if (parent == dragged) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    // ==================== 样式方法 ====================

    private String createDraggingStyle() {
        return "-fx-background-color: rgba(100, 150, 255, 0.3);" +
            "-fx-border-color: #6496ff;" +
            "-fx-border-width: 1px;" +
            "-fx-opacity: 0.8;";
    }

    private String createDropIndicatorStyle(DropPosition pos, boolean valid) {
        if (!valid) {
            return "-fx-background-color: rgba(255, 0, 0, 0.1);" +
                "-fx-border-color: #ff4444;" +
                "-fx-border-width: 2px;" +
                "-fx-border-style: dashed;";
        }

        switch (pos) {
            case BEFORE:
                return "-fx-background-color: rgba(0, 200, 0, 0.15);" +
                    "-fx-border-color: #00cc00;" +
                    "-fx-border-width: 3px 0 0 0;";
            case AFTER:
                return "-fx-background-color: rgba(0, 200, 0, 0.15);" +
                    "-fx-border-color: #00cc00;" +
                    "-fx-border-width: 0 0 3px 0;";
            case INTO:
                return "-fx-background-color: rgba(0, 150, 255, 0.2);" +
                    "-fx-border-color: #0096ff;" +
                    "-fx-border-width: 2px;";
            default:
                return "";
        }
    }

    private String createDropTooltip(TreeItem<T> dragged, TreeItem<T> target, DropPosition pos) {
        BaseDataDto draggedData = dragged.getValue();
        BaseDataDto targetData = target.getValue();
        String draggedName = draggedData.getName();
        String targetName = targetData.getName();

        switch (pos) {
            case BEFORE:
                return String.format("将 '%s' 移动到 '%s' 之前", draggedName, targetName);
            case AFTER:
                return String.format("将 '%s' 移动到 '%s' 之后", draggedName, targetName);
            case INTO:
                return String.format("将 '%s' 移动到 '%s' 内部", draggedName, targetName);
            default:
                return "";
        }
    }
}
