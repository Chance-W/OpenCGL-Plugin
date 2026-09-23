package com.opencgl.utils;

import com.alibaba.fastjson.JSON;
import com.opencgl.model.DialogStyleDto;
import com.opencgl.model.LevelDto;
import com.opencgl.model.OperateTypeEnum;
import com.opencgl.controller.DialogController;
import com.opencgl.utils.wsdl2soap.Dom4jUtil;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.Border;
import javafx.util.Callback;

import org.dom4j.DocumentException;
import org.greenrobot.eventbus.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Chance.W
 */
public class FourTreeViewUtil {
    private final Logger logger = LoggerFactory.getLogger(FourTreeViewUtil.class);

    public TreeView<String> buildTreeView(List<? extends LevelDto> testLevelDtos, Node root, EventBus eventBus) {
        logger.info("the request list is {}", JSON.toJSONString(testLevelDtos));
        TreeItem<String> rootTreeItem = new TreeItem<>("WS-Project列表");
        rootTreeItem.setExpanded(true);
        TreeView<String> treeView = new TreeView<>(rootTreeItem);
        com.opencgl.base.utils.tree.TreeViewPresentation.install(treeView);
        List<FourTreeLevel> fourTreeLevels = new ArrayList<>();
        for (LevelDto testLevelDto : testLevelDtos) {
            fourTreeLevels.add(new FourTreeLevel(testLevelDto.getFourthLevel(), testLevelDto.getThirdLevel(), testLevelDto.getSecondLevel(), testLevelDto.getFirstLevel()));
        }
        //生成树结构
        BuildFourTreeDataUtil.buildTree(fourTreeLevels, rootTreeItem);

        treeView.setFixedCellSize(30);
        treeView.setBorder(Border.EMPTY);

        ContextMenu rootMenu = new ContextMenu();
        MenuItem addConfigMenuItem = new MenuItem("添加Soap");
        rootMenu.getItems().add(addConfigMenuItem);

        ContextMenu firstMenu = new ContextMenu();
        MenuItem showDetailMenuItem = new MenuItem("详情");
        MenuItem delFirstMenuItem = new MenuItem("删除配置");
        firstMenu.getItems().addAll(showDetailMenuItem, delFirstMenuItem);

        ContextMenu addRequestMenu = new ContextMenu();
        MenuItem addRequestMenuItem = new MenuItem("添加请求");
        addRequestMenu.getItems().addAll(addRequestMenuItem);

        ContextMenu fourthMenu = new ContextMenu();
        MenuItem delRequestMenuItem = new MenuItem("删除请求");
        MenuItem modRequestMenuItem = new MenuItem("重命名");
        fourthMenu.getItems().addAll(delRequestMenuItem, modRequestMenuItem);


        treeView.setOnKeyPressed(keyEvent -> {
            if (null == treeView.getSelectionModel().getSelectedItem()) {
                return;
            }
            if (treeView.getTreeItemLevel(treeView.getSelectionModel().getSelectedItem()) == 3) {
                DialogStyleDto dialogStyleDto = DialogStyleDto.builder().type(OperateTypeEnum.QUERY).text(treeView.getSelectionModel().getSelectedItem().getValue()).build();
                eventBus.post(dialogStyleDto);
            }
        });

        treeView.setCellFactory(new Callback<>() {
            public TreeCell<String> call(TreeView<String> tv) {
                return new TreeCell<>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) {
                            setText("");
                            setGraphic(null);
                            return;
                        }
                        setText(item);
                        if (tv.getTreeItemLevel(getTreeItem()) == 0) {
                            setContextMenu(rootMenu);
                        }
                        else if (tv.getTreeItemLevel(getTreeItem()) == 1) {
                            setContextMenu(firstMenu);
                        }
                        else if (tv.getTreeItemLevel(getTreeItem()) == 2) {
                            setContextMenu(null);
                        }
                        else if (tv.getTreeItemLevel(getTreeItem()) == 3) {
                            setContextMenu(addRequestMenu);
                        }
                        else if (tv.getTreeItemLevel(getTreeItem()) == 4) {
                            setContextMenu(fourthMenu);
                        }
                    }

                    @Override
                    public void updateSelected(boolean selected) {
                        super.updateSelected(selected);
                        if (selected) {
                            TreeItem<String> item = getTreeItem();
                            if (item != null && !item.isExpanded()) {
                                recursivelySetExpanded(item, false);
                            }
                        }
                    }
                };
            }
        });

        treeView.setOnMouseClicked(mouseEvent -> {
            if (null == treeView.getSelectionModel().getSelectedItem()) {
                return;
            }
            if (treeView.getTreeItemLevel(treeView.getSelectionModel().getSelectedItem()) == 4) {
                if ("PRIMARY".equals(mouseEvent.getButton().name())) {
                    DialogStyleDto dialogStyleDto = DialogStyleDto.builder().type(OperateTypeEnum.QUERY).text(treeView.getSelectionModel().getSelectedItem().getValue()).build();
                    eventBus.post(dialogStyleDto);
                }
            }
        });

        showDetailMenuItem.setOnAction(actionEvent -> {
            try {
                DialogController.showDetail(Dom4jUtil.getRequestWsdl(treeView.getSelectionModel().getSelectedItem().getValue()), root);
            }
            catch (DocumentException e) {
                logger.error("", e);
            }
        });

        delFirstMenuItem.setOnAction(actionEvent -> {
            DialogStyleDto dialogStyleDto = DialogStyleDto.builder().type(OperateTypeEnum.DEL).level((long) treeView.getTreeItemLevel(treeView.getSelectionModel().getSelectedItem())).build();
            DialogController.secondConfirm(eventBus, dialogStyleDto, root);
        });

        addRequestMenuItem.setOnAction(actionEvent -> {
            DialogStyleDto dialogStyleDto = DialogStyleDto.builder().type(OperateTypeEnum.ADD).level(3L).build();
            eventBus.post(dialogStyleDto);
        });
        delRequestMenuItem.setOnAction(actionEvent -> {
            DialogStyleDto dialogStyleDto = DialogStyleDto.builder().type(OperateTypeEnum.DEL).level((long) treeView.getTreeItemLevel(treeView.getSelectionModel().getSelectedItem())).build();
            DialogController.secondConfirm(eventBus, dialogStyleDto, root);
        });
        modRequestMenuItem.setOnAction(actionEvent -> {
            DialogStyleDto dialogStyleDto = DialogStyleDto.builder().type(OperateTypeEnum.MOD).level(4L).text(treeView.getSelectionModel().getSelectedItem().getValue()).build();
            DialogController.dialog(eventBus, dialogStyleDto, root);
        });
        addConfigMenuItem.setOnAction(event -> DialogController.webServiceConfigure(eventBus, new DialogStyleDto(), root));
        return treeView;
    }

    private static <String> void recursivelySetExpanded(TreeItem<String> item, boolean expanded) {
        item.setExpanded(expanded);
        item.getChildren().forEach(child -> recursivelySetExpanded(child, expanded));
    }
}
