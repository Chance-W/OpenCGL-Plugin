package com.opencgl.base.utils.tree;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.opencgl.base.model.BaseDataDto;
import com.opencgl.base.service.TreeOperateService;
import com.alibaba.fastjson2.util.DateUtils;
import com.opencgl.base.utils.DialogUtil;
import com.opencgl.base.utils.PathValidatorUtil;
import com.opencgl.base.utils.TooltipUtil;
import com.opencgl.base.utils.i18n.BaseI18N;
import com.opencgl.base.view.CustomizeTreeItem;
import javafx.scene.control.*;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 树形右键菜单工厂
 * 负责创建不同类型节点的右键菜单
 *
 * @author Chance.W
 */
public class TreeMenuFactory<T extends BaseDataDto> {

    private static final Logger logger = LoggerFactory.getLogger(TreeMenuFactory.class);

    private final TreeView<T> treeView;
    private final TreeOperateService<T> service;
    private final Class<T> dataType;

    public TreeMenuFactory(TreeView<T> treeView, TreeOperateService<T> service, Class<T> dataType) {
        this.treeView = treeView;
        this.service = service;
        this.dataType = dataType;
    }

    /**
     * 创建根节点菜单
     */
    public ContextMenu createRootMenu() {
        ContextMenu menu = new ContextMenu();

        MenuItem addDirItem = new MenuItem();
        addDirItem.textProperty().bind(BaseI18N.getBinding("opencgl.base.tree.addSecondDirMenuItem"));
        addDirItem.setOnAction(e -> addNode(false));

        MenuItem addLeafItem = new MenuItem();
        addLeafItem.textProperty().bind(BaseI18N.getBinding("opencgl.base.tree.addSecondSubMenuItem"));
        addLeafItem.setOnAction(e -> addNode(true));

        menu.getItems().addAll(addDirItem, addLeafItem);

        if (service.supportImportAndExport()) {
            menu.getItems().addAll(
                    createImportMenuItem(),
                    createExportMenuItem());
        }

        return menu;
    }

    /**
     * 创建目录节点菜单
     */
    public ContextMenu createDirectoryMenu() {
        ContextMenu menu = new ContextMenu();

        MenuItem addDirItem = new MenuItem();
        addDirItem.textProperty().bind(BaseI18N.getBinding("opencgl.base.tree.addSecondDirMenuItem"));
        addDirItem.setOnAction(e -> addNode(false));

        MenuItem addLeafItem = new MenuItem();
        addLeafItem.textProperty().bind(BaseI18N.getBinding("opencgl.base.tree.addSecondSubMenuItem"));
        addLeafItem.setOnAction(e -> addNode(true));

        MenuItem modifyItem = new MenuItem();
        modifyItem.textProperty().bind(BaseI18N.getBinding("opencgl.base.tree.modDirectoryMenuItem"));
        modifyItem.setOnAction(e -> modifyNode());

        MenuItem deleteItem = new MenuItem();
        deleteItem.textProperty().bind(BaseI18N.getBinding("opencgl.base.tree.delCurrentDirMenuItem"));
        deleteItem.setOnAction(e -> deleteNode());

        menu.getItems().addAll(addDirItem, addLeafItem, modifyItem, deleteItem);

        if (service.supportImportAndExport()) {
            menu.getItems().addAll(
                    createImportMenuItem(),
                    createExportMenuItem());
        }

        return menu;
    }

    /**
     * 创建纯添加菜单 (供 Toolbar 使用)
     * 指定父节点，忽略当前选中状态
     */
    public ContextMenu createAddMenu(TreeItem<T> parent) {
        ContextMenu menu = new ContextMenu();

        MenuItem addDirItem = new MenuItem();
        addDirItem.textProperty().bind(BaseI18N.getBinding("opencgl.base.tree.addSecondDirMenuItem"));
        addDirItem.setOnAction(e -> addNode(parent, false));

        MenuItem addLeafItem = new MenuItem();
        addLeafItem.textProperty().bind(BaseI18N.getBinding("opencgl.base.tree.addSecondSubMenuItem"));
        addLeafItem.setOnAction(e -> addNode(parent, true));

        menu.getItems().addAll(addDirItem, addLeafItem);
        return menu;
    }

    /**
     * 创建叶子节点菜单
     */
    public ContextMenu createLeafMenu() {
        ContextMenu menu = new ContextMenu();

        // 如果支持自定义操作，添加操作菜单项
        if (service.supportAction()) {
            MenuItem actionItem = new MenuItem("🔗 " + service.getActionName());
            actionItem.setOnAction(e -> {
                TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    service.performAction(selected.getValue());
                }
            });
            menu.getItems().add(actionItem);
            menu.getItems().add(new SeparatorMenuItem());
        }

        MenuItem modifyItem = new MenuItem();
        modifyItem.textProperty().bind(BaseI18N.getBinding("opencgl.base.tree.modSubMenuItem"));
        modifyItem.setOnAction(e -> modifyNode());

        MenuItem deleteItem = new MenuItem();
        deleteItem.textProperty().bind(BaseI18N.getBinding("opencgl.base.tree.delCurrentSubMenuItem"));
        deleteItem.setOnAction(e -> deleteNode());

        menu.getItems().addAll(modifyItem, deleteItem);

        if (service.supportImportAndExport()) {
            menu.getItems().add(createExportMenuItem());
        }

        // 树的复制功能
        menu.getItems().add(createCopyMenuItem());

        return menu;
    }

    // ============ 导入导出菜单项 ============

    private MenuItem createImportMenuItem() {
        MenuItem item = new MenuItem();
        item.textProperty().bind(BaseI18N.getBinding("opencgl.base.tree.importMenuItem"));
        item.setOnAction(e -> importTreeData());
        return item;
    }

    private MenuItem createExportMenuItem() {
        MenuItem item = new MenuItem();
        item.textProperty().bind(BaseI18N.getBinding("opencgl.base.tree.exportMenuItem"));
        item.setOnAction(e -> exportTreeData());
        return item;
    }

    // ============ duplicate 复制菜单栏 ============
    private MenuItem createCopyMenuItem() {
        MenuItem item = new MenuItem();
        item.textProperty().bind(BaseI18N.getBinding("opencgl.base.tree.copyCurrentSubMenuItem"));
        item.setOnAction(e -> copyTreeData());
        return item;
    }

    /**
     * 导入树数据
     */
    private void importTreeData() {
        TreeItem<T> selectedItem = treeView.getSelectionModel().getSelectedItem();
        if (selectedItem == null)
            return;

        String filePath = DialogUtil.showImportDialog();
        if (!PathValidatorUtil.isValidPath(filePath)) {
            return;
        }

        try {
            File file = new File(filePath);
            String content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            List<T> importTreeLevels = JSON.parseArray(content, dataType);

            // 验证导入数据
            service.validateImportData(importTreeLevels);

            // 递归导入数据
            importAndFormatData(importTreeLevels, selectedItem);

            treeView.refresh();
            logger.info("导入完成，共 {} 条数据", importTreeLevels.size());

        } catch (IOException e) {
            logger.error("导入失败", e);
            DialogUtil.showErrorInfo("导入失败: " + e.getMessage());
        }
    }

    /**
     * 导出树数据
     */
    private void exportTreeData() {
        TreeItem<T> selectedItem = treeView.getSelectionModel().getSelectedItem();
        if (selectedItem == null)
            return;

        String filePath = DialogUtil.showExportDialog();
        if (!PathValidatorUtil.isValidPath(filePath)) {
            return;
        }

        try {
            List<T> treeData = new ArrayList<>();
            T selectedData = selectedItem.getValue();

            // 清除parentId（作为导出根节点）
            BaseDataDto baseDto = selectedData;
            Long originalParentId = baseDto.getParentId();
            baseDto.setParentId(null);

            if (Boolean.TRUE.equals(baseDto.getIsLeaf())) {
                treeData.add(selectedData);
            } else {
                treeData.add(selectedData);
                collectTreeItemData(treeData, selectedItem);
            }

            // 恢复原始parentId
            baseDto.setParentId(originalParentId);

            String newFileName = baseDto.getName() + "_export_" + DateUtils.format(new Date()) + ".json";
            File writeFile = new File(filePath + File.separator + newFileName);
            FileUtils.write(writeFile, JSON.toJSONString(treeData,
                    SerializerFeature.PrettyFormat,
                    SerializerFeature.DisableCircularReferenceDetect), StandardCharsets.UTF_8);

            // 询问是否打开文件
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setHeaderText("");
            alert.setContentText(BaseI18N.getOrDefault("opencgl.base.export.success.open.question"));
            alert.showAndWait();

            if (alert.getResult() == ButtonType.OK) {
                Desktop.getDesktop().open(writeFile);
            }

            logger.info("导出完成: {}", writeFile.getAbsolutePath());

        } catch (IOException e) {
            logger.error("导出失败", e);
            DialogUtil.showErrorInfo("导出失败: " + e.getMessage());
        }
    }

    /**
     * 复制树数据 (公开以供 Toolbar 调用)
     */
    public void copyTreeData() {
        TreeItem<T> selectedItem = treeView.getSelectionModel().getSelectedItem();
        if (selectedItem == null)
            return;

        T value = selectedItem.getValue();
        TreeItem<T> parent = selectedItem.getParent();
        if (parent == null)
            return;

        try {
            // Priority 1: Try to use the service's copy method if implemented
            CustomizeTreeItem<T> copy = service.copy(value);

            // Priority 2: Fallback to manual copy implementation
            if (copy == null) {
                // 使用 JSON 序列化进行深拷贝，保留所有字段数据
                T copyValue = JSON.parseObject(JSON.toJSONString(value), dataType);
                copyValue.setId(null); // 重置ID，确保是新数据
                // Explicitly use the current parent's ID
                copyValue.setParentId(parent.getValue().getId());
                copyValue.setIsLeaf(value.getIsLeaf());
                copyValue.setName(value.getName() + " Copy");

                // 设置排序号为最后 (追加模式)
                copyValue.setSortOrder(parent.getChildren().size());

                copy = service.add(copyValue);
            }

            if (copy != null) {
                // 插入到当前项的后面
                int index = parent.getChildren().indexOf(selectedItem);
                if (index >= 0 && index < parent.getChildren().size()) {
                    parent.getChildren().add(index + 1, copy);
                } else {
                    parent.getChildren().add(copy);
                }

                treeView.refresh();

                // 选中新项
                treeView.getSelectionModel().clearSelection();
                treeView.getSelectionModel().select(copy);

                // 重新排序兄弟节点
                List<T> siblings = parent.getChildren().stream()
                        .map(TreeItem::getValue)
                        .collect(Collectors.toList());
                service.batchReorder(siblings);

                TooltipUtil.showToast(treeView.getParent(), "复制成功");
            }
        } catch (Exception e) {
            logger.error("Copy failed", e);
            TooltipUtil.showToast(treeView.getParent(), "复制失败: " + e.getMessage());
        }
    }

    /**
     * 递归收集树节点数据
     */
    private void collectTreeItemData(List<T> treeData, TreeItem<T> parentItem) {
        for (TreeItem<T> child : parentItem.getChildren()) {
            treeData.add(child.getValue());
            if (!child.getChildren().isEmpty()) {
                collectTreeItemData(treeData, child);
            }
        }
    }

    /**
     * 格式化并导入数据
     */
    private void importAndFormatData(List<T> importData, TreeItem<T> targetParent) {
        if (importData == null || importData.isEmpty())
            return;

        // 按parentId分组
        Map<Long, List<T>> parentMap = new HashMap<>();
        Long rootParentId = null;

        for (T item : importData) {
            Long parentId = item.getParentId();
            if (parentId == null) {
                parentId = item.getId();
            }
            parentMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(item);
        }

        // 递归插入节点
        List<T> rootItems = parentMap.get(null);
        if (rootItems != null) {
            for (T rootItem : rootItems) {
                importNodeRecursively(rootItem, targetParent, parentMap);
            }
        }
    }

    /**
     * 递归导入单个节点
     */
    private void importNodeRecursively(T item, TreeItem<T> parentTreeItem, Map<Long, List<T>> parentMap) {
        BaseDataDto dto = item;
        Long oldId = dto.getId();

        // 设置新的父节点ID
        dto.setParentId(parentTreeItem.getValue().getId());

        // 插入到数据库并获取新ID
        CustomizeTreeItem<T> newTreeItem = service.importData(item);
        if (newTreeItem == null)
            return;

        parentTreeItem.getChildren().add(newTreeItem);
        parentTreeItem.setExpanded(true);

        // 递归处理子节点
        List<T> children = parentMap.get(oldId);
        if (children != null) {
            for (T child : children) {
                importNodeRecursively(child, newTreeItem, parentMap);
            }
        }
    }

    // ============ 基础操作 ============

    private void addNode(boolean isLeaf) {
        TreeItem<T> selectedItem = treeView.getSelectionModel().getSelectedItem();
        addNode(selectedItem, isLeaf);
    }

    /**
     * 添加节点 (公开以供 Toolbar 调用)
     * 
     * @param parent 父节点 Item (如果为 null 则不执行)
     * @param isLeaf 是否为叶子节点
     */
    public void addNode(TreeItem<T> parent, boolean isLeaf) {
        if (parent == null)
            return;

        String name = DialogUtil.show();
        if (name == null || name.isEmpty())
            return;

        try {
            T newData = dataType.getDeclaredConstructor().newInstance();
            newData.setParentId(parent.getValue().getId());
            newData.setName(name);
            newData.setIsLeaf(isLeaf);

            // 设置排序号为最后
            int sortOrder = parent.getChildren().size();
            newData.setSortOrder(sortOrder);

            CustomizeTreeItem<T> newItem = service.add(newData);
            parent.getChildren().add(newItem);
            parent.setExpanded(true);
            treeView.getSelectionModel().clearSelection();
            treeView.getSelectionModel().select(newItem);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                | NoSuchMethodException e) {
            throw new RuntimeException("创建节点失败", e);
        }
    }

    private void modifyNode() {
        TreeItem<T> selectedItem = treeView.getSelectionModel().getSelectedItem();
        if (selectedItem == null)
            return;

        T data = selectedItem.getValue();
        BaseDataDto dto = data;
        String newName = DialogUtil.show(dto.getName());
        if (newName == null || newName.isEmpty())
            return;

        dto.setName(newName);
        CustomizeTreeItem<T> updated = service.update(data);
        selectedItem.setValue(updated.getValue());
        treeView.refresh();
    }

    /**
     * 删除节点 (公开以供 Toolbar 调用)
     */
    public void deleteNode() {
        // 获取所有选中的项（支持多选）
        List<TreeItem<T>> selectedItems = new ArrayList<>(treeView.getSelectionModel().getSelectedItems());
        if (selectedItems.isEmpty())
            return;

        // 过滤掉根节点和空项
        selectedItems = selectedItems.stream()
                .filter(item -> item != null && item.getValue() != null)
                .filter(item -> item.getValue().getId() != null && item.getValue().getId() != 0)
                .toList();

        if (selectedItems.isEmpty())
            return;

        Boolean confirm = DialogUtil.deleteConfirm();
        if (confirm == null || !confirm)
            return;

        // 批量删除所有选中项
        for (TreeItem<T> item : selectedItems) {
            deleteRecursively(item);
        }
    }

    private void deleteRecursively(TreeItem<T> item) {
        // 先删除所有子节点
        for (TreeItem<T> child : new ArrayList<>(item.getChildren())) {
            deleteRecursively(child);
        }

        // 再删除当前节点
        service.delete(item.getValue());

        TreeItem<T> parent = item.getParent();
        if (parent != null) {
            parent.getChildren().remove(item);
        }
    }
}
