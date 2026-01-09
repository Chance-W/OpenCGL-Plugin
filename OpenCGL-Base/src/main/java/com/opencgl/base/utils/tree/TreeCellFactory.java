package com.opencgl.base.utils.tree;

import com.opencgl.base.model.BaseDataDto;
import com.opencgl.base.service.TreeOperateService;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.util.Callback;

/**
 * 树形单元格工厂
 * 负责创建TreeCell，集成右键菜单和拖拽功能
 * 
 * @author Chance.W
 */
public class TreeCellFactory<T extends BaseDataDto> implements Callback<TreeView<T>, TreeCell<T>> {

    private final TreeDragHandler<T> dragHandler;
    private final boolean enableDragDrop;
    private final String cellStyle;
    private final java.util.function.Function<T, ContextMenu> contextMenuFactory;

    private ContextMenu rootMenu;
    private ContextMenu directoryMenu;
    private ContextMenu leafMenu;

    /**
     * 兼容旧构造函数（无样式）
     */
    public TreeCellFactory(TreeView<T> treeView, TreeOperateService<T> service,
            Class<T> dataType, boolean enableDragDrop) {
        this(treeView, service, dataType, enableDragDrop, null, null);
    }

    /**
     * 兼容旧构造函数（支持样式）
     */
    public TreeCellFactory(TreeView<T> treeView, TreeOperateService<T> service,
            Class<T> dataType, boolean enableDragDrop, String cellStyle) {
        this(treeView, service, dataType, enableDragDrop, cellStyle, null);
    }

    /**
     * 新构造函数（支持自定义右键菜单）
     */
    public TreeCellFactory(TreeView<T> treeView, TreeOperateService<T> service,
            Class<T> dataType, boolean enableDragDrop, String cellStyle,
            java.util.function.Function<T, ContextMenu> contextMenuFactory) {
        TreeMenuFactory<T> actualMenuFactory = new TreeMenuFactory<>(treeView, service, dataType);
        this.dragHandler = new TreeDragHandler<>(treeView, service);
        this.enableDragDrop = enableDragDrop && service.supportDragDrop();
        this.cellStyle = cellStyle;
        this.contextMenuFactory = contextMenuFactory;

        // 预先创建菜单
        this.rootMenu = actualMenuFactory.createRootMenu();
        this.directoryMenu = actualMenuFactory.createDirectoryMenu();
        this.leafMenu = actualMenuFactory.createLeafMenu();
    }

    @Override
    public TreeCell<T> call(TreeView<T> treeView) {
        TreeCell<T> cell = new TreeCell<T>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    textProperty().unbind();
                    setText(null);
                    setGraphic(null);
                    setContextMenu(null);
                    setTooltip(null);
                    // 空单元格也应用样式（避免白色闪烁）
                    if (cellStyle != null) {
                        setStyle(cellStyle);
                    }
                    return;
                }

                // 使用BaseDataDto方法访问
                BaseDataDto dto = item;

                textProperty().unbind();
                if (getTreeItem() instanceof com.opencgl.base.view.CustomizeTreeItem) {
                    com.opencgl.base.view.CustomizeTreeItem<?> cti = (com.opencgl.base.view.CustomizeTreeItem<?>) getTreeItem();
                    if (cti.nameBindingProperty().isBound()) {
                        textProperty().bind(cti.nameBindingProperty());
                    } else {
                        setText(dto.getName());
                    }
                } else {
                    setText(dto.getName());
                }

                setGraphic(null);

                // 应用单元格样式
                if (cellStyle != null) {
                    setStyle(cellStyle);
                }

                // 为SSH连接节点添加Tooltip
                if (Boolean.TRUE.equals(dto.getIsLeaf())) {
                    try {
                        // 尝试获取SSH连接信息（如果dto有这些字段）
                        java.lang.reflect.Method getHost = dto.getClass().getMethod("getHost");
                        java.lang.reflect.Method getPort = dto.getClass().getMethod("getPort");
                        java.lang.reflect.Method getUsername = dto.getClass().getMethod("getUsername");

                        String host = (String) getHost.invoke(dto);
                        Object portObj = getPort.invoke(dto);
                        String username = (String) getUsername.invoke(dto);

                        if (host != null && username != null) {
                            String port = portObj != null ? portObj.toString() : "22";
                            String tooltipText = String.format("ssh %s@%s -p %s", username, host, port);
                            setTooltip(new javafx.scene.control.Tooltip(tooltipText));
                        } else {
                            setTooltip(null);
                        }
                    } catch (Exception e) {
                        // 如果不是SSH连接DTO，忽略tooltip
                        setTooltip(null);
                    }
                } else {
                    setTooltip(null);
                }

                // 设置右键菜单
                if (contextMenuFactory != null) {
                    setContextMenu(contextMenuFactory.apply((T) dto));
                } else if (dto.getId() == null || dto.getId() == 0) {
                    setContextMenu(rootMenu);
                } else if (Boolean.TRUE.equals(dto.getIsLeaf())) {
                    setContextMenu(leafMenu);
                } else {
                    setContextMenu(directoryMenu);
                }
            }

            @Override
            public void updateSelected(boolean selected) {
                super.updateSelected(selected);
                if (selected) {
                    TreeItem<T> treeItem = getTreeItem();
                    if (treeItem != null && !treeItem.isExpanded() && !treeItem.isLeaf()) {
                        // 可选：选中时不自动展开
                    }
                }
            }
        };

        // 设置拖拽事件
        if (enableDragDrop) {
            dragHandler.setupDragEvents(cell);
        }

        return cell;
    }
}
