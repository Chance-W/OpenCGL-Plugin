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
import com.opencgl.base.utils.i18n.BASE18N;
import com.opencgl.base.view.CustomizeTreeItem;
import com.sun.javafx.scene.control.skin.resources.ControlResources;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.Border;
import javafx.util.Callback;

/**
 * @author Chance.W
 */
@SuppressWarnings({"unused", "DanglingJavadoc"})
public class TreeViewUtil<T extends BaseDataDto> {
    private static final Logger logger = LoggerFactory.getLogger(TreeViewUtil.class);

    public static <T extends BaseDataDto> TreeView<T> buildTreeView(List<T> treeLevels, Node root, TreeOperateService<T> treeOperateService, Class<T> dataType)
        throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        T rootDataDto = dataType.getDeclaredConstructor().newInstance();
        rootDataDto.setId(0L);
        rootDataDto.setName(BASE18N.getOrDefault("opencgl.base.tree.firstLevel.name"));
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
        MenuItem addSecondDirMenuItem = new MenuItem(BASE18N.getOrDefault("opencgl.base.tree.addSecondDirMenuItem"));//"添加子目录"
        MenuItem addSecondSubMenuItem = new MenuItem(BASE18N.getOrDefault("opencgl.base.tree.addSecondSubMenuItem"));//"添加子节点"
        if (treeOperateService.supportImportAndExport()) {
            MenuItem importRootMenuItem = createMenuItem(treeOperateService, dataType, treeView, BASE18N.getOrDefault("opencgl.base.tree.importMenuItem"), 0);
            MenuItem exportRootMenuItem = createMenuItem(treeOperateService, dataType, treeView, BASE18N.getOrDefault("opencgl.base.tree.exportMenuItem"), 1);
            rootMenu.getItems().addAll(addSecondDirMenuItem, addSecondSubMenuItem, importRootMenuItem, exportRootMenuItem);
        }
        else {
            rootMenu.getItems().addAll(addSecondDirMenuItem, addSecondSubMenuItem);
        }

        // 目录菜单context
        ContextMenu dirMenu = new ContextMenu();
        MenuItem addSubDirMenuItem = new MenuItem(BASE18N.getOrDefault("opencgl.base.tree.addSecondDirMenuItem"));// "添加子目录"
        MenuItem addSubMenuItem = new MenuItem(BASE18N.getOrDefault("opencgl.base.tree.addSecondSubMenuItem"));//"添加子节点"
        MenuItem delCurrentDirMenuItem = new MenuItem(BASE18N.getOrDefault("opencgl.base.tree.delCurrentDirMenuItem"));//"删除当前目录"
        MenuItem modDirectoryMenuItem = new MenuItem(BASE18N.getOrDefault("opencgl.base.tree.modDirectoryMenuItem")); // "修改当前目录"
        if (treeOperateService.supportImportAndExport()) {
            MenuItem importDirMenuItem = createMenuItem(treeOperateService, dataType, treeView, BASE18N.getOrDefault("opencgl.base.tree.importMenuItem"), 0);
            MenuItem exportDirMenuItem = createMenuItem(treeOperateService, dataType, treeView, BASE18N.getOrDefault("opencgl.base.tree.exportMenuItem"), 1);
            dirMenu.getItems().addAll(addSubDirMenuItem, addSubMenuItem, delCurrentDirMenuItem, modDirectoryMenuItem, importDirMenuItem, exportDirMenuItem);
        }
        else {
            dirMenu.getItems().addAll(addSubDirMenuItem, addSubMenuItem, delCurrentDirMenuItem, modDirectoryMenuItem);
        }


        // 子节点context
        ContextMenu subMenu = new ContextMenu();
        MenuItem modSubMenuItem = new MenuItem(BASE18N.getOrDefault("opencgl.base.tree.modSubMenuItem"));//"修改当前节点"
        MenuItem delCurrentSubMenuItem = new MenuItem(BASE18N.getOrDefault("opencgl.base.tree.delCurrentSubMenuItem")); //"删除当前节点"
        if (treeOperateService.supportImportAndExport()) {
            MenuItem exportSubMenuItem = createMenuItem(treeOperateService, dataType, treeView, BASE18N.getOrDefault("opencgl.base.tree.exportMenuItem"), 1);
            subMenu.getItems().addAll(modSubMenuItem, delCurrentSubMenuItem, exportSubMenuItem);
        }
        else {
            subMenu.getItems().addAll(modSubMenuItem, delCurrentSubMenuItem);
        }

        // 设置节点取对象name
        treeView.setCellFactory(new Callback<>() {
            public TreeCell<T> call(TreeView<T> tv) {
                return new TreeCell<>() {
                    @Override
                    protected void updateItem(T item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) {
                            setText("");
                            setGraphic(null);
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
                };
            }
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
            alert.setContentText(BASE18N.getOrDefault("opencgl.base.export.success.open.question"));
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

    /**
     * // 弹框增加和修改
     * private static <T extends BaseDataDto> void alertOperateTitle(Node node, String nodeName, TreeView<T> jfxTreeView, Function<String, CustomizeTreeItem<T>> function) {
     * String text = DialogUtil.show(nodeName);
     * CustomizeTreeItem<T> CustomizeTreeItem = function.apply(text);
     * }
     */
}
