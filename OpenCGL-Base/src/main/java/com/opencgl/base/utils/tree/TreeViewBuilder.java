package com.opencgl.base.utils.tree;

import com.opencgl.base.view.CustomizeTreeItem;
import com.opencgl.base.model.BaseDataDto;
import com.opencgl.base.service.TreeOperateService;
import com.opencgl.base.utils.TooltipUtil;
import com.opencgl.base.utils.i18n.BaseI18N;
import io.github.palexdev.mfxresources.fonts.MFXFontIcon;
import javafx.application.Platform;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通用TreeView构建器
 * T必须继承BaseDataDto以确保有基本字段
 */
public class TreeViewBuilder<T extends BaseDataDto> {
    private static final Logger logger = LoggerFactory.getLogger(TreeViewBuilder.class);

    private TreeOperateService<T> service;
    private List<T> treeData;
    private Class<T> dataType;

    // UI配置
    private boolean showRoot = true;
    private boolean rootExpanded = true;
    private boolean editable = false;
    private boolean searchEnabled = false; // Default false
    private StringBinding searchPrompt = BaseI18N.getBinding("opencgl.base.tree.search");
    private boolean enableDragDrop = true;
    private boolean enableToolbar = false;
    private double cellHeight = 30.0;
    private String cellStyle = null;

    // 功能开关
    private boolean expandAll = false;

    // 回调
    private Consumer<T> onSelectCallback;
    private Consumer<T> onDoubleClickCallback;
    private Function<T, ContextMenu> contextMenuFactory;
    private Consumer<TreeView<T>> onTreeCreatedCallback;
    private Supplier<T> locateTargetSupplier; // Supplier to get the current display data for locating

    // Menu Factory
    private TreeMenuFactory<T> menuFactory;

    // 内部状态
    private CustomizeTreeItem<T> rootItem;
    private TreeView<T> builtTreeView;
    private T lastSelectedData;

    public TreeViewBuilder() {
    }

    public TreeViewBuilder<T> service(TreeOperateService<T> service) {
        this.service = service;
        return this;
    }

    public TreeViewBuilder<T> data(List<T> treeData) {
        this.treeData = treeData;
        return this;
    }

    public TreeViewBuilder<T> dataType(Class<T> dataType) {
        this.dataType = dataType;
        return this;
    }

    public TreeViewBuilder<T> showRoot(boolean showRoot) {
        this.showRoot = showRoot;
        return this;
    }

    public TreeViewBuilder<T> rootExpanded(boolean rootExpanded) {
        this.rootExpanded = rootExpanded;
        return this;
    }

    public TreeViewBuilder<T> editable(boolean editable) {
        this.editable = editable;
        return this;
    }

    public TreeViewBuilder<T> enableDragDrop(boolean enable) {
        this.enableDragDrop = enable;
        return this;
    }

    public TreeViewBuilder<T> enableSearch() {
        this.searchEnabled = true;
        return this;
    }

    public TreeViewBuilder<T> enableSearch(boolean searchEnabled) {
        this.searchEnabled = searchEnabled;
        return this;
    }

    public TreeViewBuilder<T> enableToolbar(boolean enable) {
        this.enableToolbar = enable;
        return this;
    }

    public TreeViewBuilder<T> searchPrompt(StringBinding prompt) {
        this.searchPrompt = prompt;
        // Implicitly enable search if prompt is set? Or just set prompt.
        // Original code: this.searchEnabled = true;
        this.searchEnabled = true;
        return this;
    }

    public TreeViewBuilder<T> searchPrompt(String prompt) {
        this.searchPrompt = javafx.beans.binding.Bindings.createStringBinding(() -> prompt);
        this.searchEnabled = true;
        return this;
    }

    public TreeViewBuilder<T> expandAll(boolean expandAll) {
        this.expandAll = expandAll;
        return this;
    }

    public TreeViewBuilder<T> onSelect(Consumer<T> callback) {
        this.onSelectCallback = callback;
        return this;
    }

    public TreeViewBuilder<T> onDoubleClick(Consumer<T> callback) {
        this.onDoubleClickCallback = callback;
        return this;
    }

    public TreeViewBuilder<T> contextMenuFactory(Function<T, ContextMenu> factory) {
        this.contextMenuFactory = factory;
        return this;
    }

    public TreeViewBuilder<T> onTreeCreated(Consumer<TreeView<T>> callback) {
        this.onTreeCreatedCallback = callback;
        return this;
    }

    public TreeViewBuilder<T> locateTargetSupplier(Supplier<T> supplier) {
        this.locateTargetSupplier = supplier;
        return this;
    }

    public TreeViewBuilder<T> cellHeight(double height) {
        this.cellHeight = height;
        return this;
    }

    public TreeViewBuilder<T> cellStyle(String style) {
        this.cellStyle = style;
        return this;
    }

    public VBox build() {
        validateParameters();

        VBox container = new VBox(5);
        container.setPadding(new Insets(0, 0, 0, 2));
        container.setFillWidth(true);
        // container.getStyleClass().add("tree-container"); // Optional

        try {
            // 1. 创建 Root
            T rootData = dataType.getConstructor().newInstance();
            rootData.setId(0L);
            rootData.setName(""); // name value no longer primary when bound
            rootData.setParentId(-1L);
            rootData.setIsLeaf(false);

            this.rootItem = new CustomizeTreeItem<>(rootData);
            this.rootItem.nameBindingProperty().bind(BaseI18N.getBinding("opencgl.base.tree.firstLevel.name"));
            this.rootItem.setExpanded(rootExpanded);

            // 2. 构建 TreeView
            TreeView<T> treeView = new TreeView<>(rootItem);
            this.builtTreeView = treeView;
            treeView.setShowRoot(showRoot);
            treeView.setEditable(editable);
            treeView.setFixedCellSize(cellHeight);

            // 3. 加载数据
            if (treeData == null) {
                treeData = service.queryAll();
            }
            // 初始构建
            buildTreeStructure(this.treeData, rootItem);
            if (expandAll)
                expandAllNodes(rootItem);

            // Initialize Menu Factory
            this.menuFactory = new TreeMenuFactory<>(treeView, service, dataType);

            // 4. 设置 CellFactory using the helper class
            // Use the constructor: TreeCellFactory(TreeView<T> treeView,
            // TreeOperateService<T> service, Class<T> dataType, boolean enableDragDrop,
            // String cellStyle, Function<T, ContextMenu> contextMenuFactory)
            treeView.setCellFactory(new TreeCellFactory<>(treeView, service, dataType, enableDragDrop, cellStyle,
                    this.contextMenuFactory));

            // 选择事件
            treeView.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
                if (newVal != null && newVal.getValue() != null) {
                    this.lastSelectedData = newVal.getValue();
                    if (onSelectCallback != null) {
                        onSelectCallback.accept(newVal.getValue());
                    }
                    // Service notification for display change relies on leaf check typically,
                    // but here we might just let the callback handle it or do it.
                    // DubboWidgetController handles selection in onSelectCallback mostly, BUT
                    // built-in behavior might be needed.
                    if (Boolean.TRUE.equals(newVal.getValue().getIsLeaf())) {
                        service.changeToDisplay(newVal.getValue());
                    }
                }
            });

            // Toolbar
            if (enableToolbar) {
                HBox toolbar = buildToolbar(treeView);
                container.getChildren().add(toolbar);
            }

            // Search Field
            setupSearchEvents(container);

            // 5. 添加 TreeView 并设置 VGrow
            container.getChildren().add(treeView);
            VBox.setVgrow(treeView, Priority.ALWAYS); // 树自动填满剩余垂直空间

            logger.info("TreeView构建完成，共 {} 个节点", treeData != null ? treeData.size() : 0);

            // 触发创建回调
            if (this.onTreeCreatedCallback != null) {
                this.onTreeCreatedCallback.accept(treeView);
            }

            return container;

        } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                | NoSuchMethodException e) {
            throw new RuntimeException("构建TreeView失败", e);
        }
    }

    private HBox buildToolbar(TreeView<T> treeView) {
        HBox toolbar = new HBox(5);
        toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        toolbar.setPadding(new javafx.geometry.Insets(2, 5, 2, 5));

        // Add Button (+)
        Button addBtn = createIconBtn("fas-plus", BaseI18N.getBinding("opencgl.base.tree.action.add"), e -> {
            // Determine Context Parent
            TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
            TreeItem<T> targetParent;

            if (selected == null || selected.getValue() == null) {
                targetParent = rootItem;
            } else {
                if (Boolean.TRUE.equals(selected.getValue().getIsLeaf())) {
                    // If leaf is selected, add as sibling (add to parent)
                    targetParent = selected.getParent();
                } else {
                    // If directory is selected, add as child (add to self)
                    targetParent = selected;
                }
            }

            if (targetParent == null)
                targetParent = rootItem;

            // Show Add Menu (Reusing factory logic)
            ContextMenu addMenu = menuFactory.createAddMenu(targetParent);

            // Show popup
            addMenu.show((javafx.scene.Node) e.getSource(), Side.BOTTOM, 0, 0);
        });

        // Copy Button
        Button copyBtn = createIconBtn("fas-copy", BaseI18N.getBinding("opencgl.base.tree.action.copy"), e -> {
            TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected != null && Boolean.TRUE.equals(selected.getValue().getIsLeaf())) {
                menuFactory.copyTreeData();
            } else {
                TooltipUtil.showToast((Node) e.getSource(),
                        BaseI18N.getOrDefault("opencgl.base.tree.action.onlyLeaf", "Only child nodes can be operated"));
            }
        });

        // Delete Button
        Button deleteBtn = createIconBtn("fas-trash", BaseI18N.getBinding("opencgl.base.tree.action.delete"), e -> {
            TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected != null && Boolean.TRUE.equals(selected.getValue().getIsLeaf())) {
                menuFactory.deleteNode();
            } else {
                TooltipUtil.showToast((javafx.scene.Node) e.getSource(),
                        BaseI18N.getOrDefault("opencgl.base.tree.action.onlyLeaf", "Only child nodes can be operated"));
            }
        });

        // Locate Button (Target)
        Button locateBtn = createIconBtn("fas-crosshairs", BaseI18N.getBinding("opencgl.base.tree.action.locate"),
                e -> {
                    // Determine target: Supplier (Display) > Last Selection > Current Selection
                    T targetData = null;
                    if (this.locateTargetSupplier != null) {
                        targetData = this.locateTargetSupplier.get();
                    }
                    if (targetData == null) {
                        targetData = this.lastSelectedData;
                    }

                    if (targetData != null) {
                        TreeItem<T> targetItem = findTreeItem(rootItem, targetData.getId());

                        if (targetItem != null) {
                            // 1. Expand parents
                            TreeItem<T> parent = targetItem.getParent();
                            while (parent != null) {
                                parent.setExpanded(true);
                                parent = parent.getParent();
                            }

                            // 2. Clear previous selection ensures only target is selected
                            treeView.getSelectionModel().clearSelection();
                            treeView.getSelectionModel().select(targetItem);

                            // 3. Scroll
                            int index = treeView.getRow(targetItem);
                            if (index >= 0) {
                                int targetIndex = Math.max(0, index - 5);
                                treeView.scrollTo(targetIndex);
                                treeView.requestFocus();
                            }

                            // 4. Trigger callback/display update
                            if (onSelectCallback != null) {
                                onSelectCallback.accept(targetItem.getValue());
                            }
                            if (Boolean.TRUE.equals(targetItem.getValue().getIsLeaf())) {
                                service.changeToDisplay(targetItem.getValue());
                            }
                        } else {
                            logger.warn("Locate: Item {} not found", targetData.getId());
                        }
                    } else {
                        int index = treeView.getSelectionModel().getSelectedIndex();
                        if (index >= 0) {
                            treeView.scrollTo(Math.max(0, index - 5));
                            treeView.requestFocus();
                        }
                    }
                });

        // Expand All Button
        Button expandAllBtn = createIconBtn("fas-expand", BaseI18N.getBinding("opencgl.base.tree.action.expand"),
                e -> expandAllNodes(rootItem));

        // Collapse All
        Button collapseAllBtn = createIconBtn("fas-compress", BaseI18N.getBinding("opencgl.base.tree.action.collapse"),
                e -> collapseAllNodes(rootItem));

        return new HBox(5, addBtn, copyBtn, deleteBtn, locateBtn, collapseAllBtn, expandAllBtn);
    }

    private Button createIconBtn(String iconDescription, javafx.beans.binding.StringBinding tooltipBinding,
            EventHandler<MouseEvent> handler) {
        Button btn = new Button();
        MFXFontIcon icon = new MFXFontIcon(iconDescription, 16);
        // icon color managed by CSS

        btn.setGraphic(icon);
        Tooltip tooltip = new Tooltip();
        tooltip.textProperty().bind(tooltipBinding);
        btn.setTooltip(tooltip);
        btn.getStyleClass().add("icon-button");
        // btn.setStyle("-fx-background-color: transparent; -fx-padding: 3; -fx-cursor:
        // hand;"); // Moved to CSS
        // btn.setMinSize(28, 28); // Moved to CSS

        // Interaction colors managed by CSS (:hover)

        btn.setOnMouseClicked(handler);
        return btn;
    }

    private void performGenericAdd(TreeView<T> treeView) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(BaseI18N.getOrDefault("opencgl.base.tree.add.title", "Add New"));
        dialog.setHeaderText(null);
        dialog.setContentText(BaseI18N.getOrDefault("opencgl.base.tree.add.prompt", "Enter name:"));

        dialog.showAndWait().ifPresent(name -> {
            if (name.trim().isEmpty())
                return;
            try {
                T newItem = dataType.getDeclaredConstructor().newInstance();
                newItem.setName(name);

                TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
                Long targetParentId;

                // 1. Determine Target Parent ID
                if (selected == null || selected.getValue() == null) {
                    // No selection -> Add to Root
                    targetParentId = 0L;
                } else {
                    T selectedData = selected.getValue();
                    if (Boolean.TRUE.equals(selectedData.getIsLeaf())) {
                        // Selected is Leaf -> Add as Sibling (to Parent)
                        targetParentId = selectedData.getParentId();
                    } else {
                        // Selected is Directory -> Add as Child
                        targetParentId = selectedData.getId();
                    }
                }

                newItem.setParentId(targetParentId);

                // 2. Determine Type (Group vs Leaf)
                // If parent is Root (0), create Group (Directory).
                // Otherwise create Leaf (Item).
                // This enforces: Root -> Group -> Item structure.
                if (targetParentId == 0L) {
                    newItem.setIsLeaf(false);
                } else {
                    newItem.setIsLeaf(true);
                }

                CustomizeTreeItem<T> createdItem = service.add(newItem);

                if (createdItem != null) {
                    TreeItem<T> parentItem = findTreeItem(rootItem, createdItem.getValue().getParentId());
                    if (parentItem == null)
                        parentItem = rootItem;
                    parentItem.getChildren().add(createdItem);
                    parentItem.setExpanded(true);
                    treeView.getSelectionModel().clearSelection();
                    treeView.getSelectionModel().select(createdItem);
                } else {
                    filterTree(null);
                }

            } catch (Exception e) {
                logger.error("Failed to create item", e);
            }
        });
    }

    // Helper to search tree item
    private TreeItem<T> findTreeItem(TreeItem<T> root, Long id) {
        if (root.getValue() != null && Objects.equals(root.getValue().getId(), id))
            return root;
        for (TreeItem<T> child : root.getChildren()) {
            TreeItem<T> found = findTreeItem(child, id);
            if (found != null)
                return found;
        }
        return null;
    }

    private void collapseAllNodes(TreeItem<T> item) {
        if (item != null && !item.isLeaf()) {
            item.setExpanded(false);
            for (TreeItem<T> child : item.getChildren()) {
                collapseAllNodes(child);
            }
        }
    }

    private void expandAllNodes(TreeItem<T> item) {
        if (item != null && !item.isLeaf()) {
            item.setExpanded(true);
            for (TreeItem<T> child : item.getChildren()) {
                expandAllNodes(child);
            }
        }
    }

    private void setupSearchEvents(VBox container) {
        // 搜索支持
        if (searchEnabled) {
            TextField searchField = new TextField();
            searchField.promptTextProperty().bind(searchPrompt);
            searchField.getStyleClass().add("tree-search-field");
            searchField.setMaxWidth(Double.MAX_VALUE); // 允许无限横向拉伸
            searchField.setStyle("-fx-pref-height: 30;");

            // 绑定搜索逻辑
            searchField.textProperty().addListener((obs, old, val) -> filterTree(val));
            container.getChildren().add(searchField);
        }
    }

    private void validateParameters() {
        if (service == null) {
            throw new IllegalStateException("TreeOperateService不能为空");
        }
        if (dataType == null) {
            throw new IllegalStateException("dataType不能为空");
        }
    }

    /**
     * 执行树过滤（基于重构树结构）
     */
    private void filterTree(String keyword) {
        // Same logic as before
        if (builtTreeView == null || rootItem == null)
            return;

        // 清空当前树节点（保留 root）
        rootItem.getChildren().clear();

        if (keyword == null || keyword.trim().isEmpty()) {
            // 搜索为空，重建完整树
            buildTreeStructure(this.treeData, rootItem);
            if (rootExpanded)
                rootItem.setExpanded(true);
            if (expandAll)
                expandAllNodes(rootItem);
        } else {
            // 执行带过滤的重建
            String lowerKeyword = keyword.toLowerCase().trim();
            Set<Long> matchedIds = new HashSet<>();
            Map<Long, T> dataMap = new HashMap<>(); // Prefer HashMap if not stream collecting directly
            if (treeData != null) {
                dataMap = treeData.stream().collect(Collectors.toMap(T::getId, t -> t));
            }

            // 1. 找出所有名称匹配的节点
            if (treeData != null) {
                for (T item : treeData) {
                    if (item.getName() != null && item.getName().toLowerCase().contains(lowerKeyword)) {
                        // 标记当前节点
                        matchedIds.add(item.getId());
                        // 标记所有父级（保证路径可见）
                        markParents(item, dataMap, matchedIds);
                        // 标记所有子级（保证文件夹内容可见）
                        markChildren(item, treeData, matchedIds);
                    }
                }
            }

            // 2. 过滤数据
            List<T> filteredData = new ArrayList<>();
            if (treeData != null) {
                filteredData = treeData.stream()
                        .filter(item -> matchedIds.contains(item.getId()))
                        .collect(Collectors.toList());
            }

            // 3. 重建树
            buildTreeStructure(filteredData, rootItem);

            // 4. 展开所有可见节点以便查看结果
            expandAllNodes(rootItem);
        }
    }

    // 递归标记父节点
    private void markParents(T item, Map<Long, T> dataMap, Set<Long> matchedIds) {
        Long parentId = item.getParentId();
        if (parentId != null && parentId != 0) {
            if (matchedIds.add(parentId)) { // 如果父节点未被添加过，则继续递归
                T parent = dataMap.get(parentId);
                if (parent != null) {
                    markParents(parent, dataMap, matchedIds);
                }
            }
        }
    }

    // 递归标记子节点
    private void markChildren(T parent, List<T> allData, Set<Long> matchedIds) {
        for (T item : allData) {
            if (Objects.equals(item.getParentId(), parent.getId())) {
                if (matchedIds.add(item.getId())) { // 如果子节点未被添加过
                    markChildren(item, allData, matchedIds);
                }
            }
        }
    }

    // 构建树形结构的核心逻辑
    private void buildTreeStructure(List<T> data, CustomizeTreeItem<T> root) {
        if (data == null)
            return;
        // 按sortOrder排序
        List<T> sortedData = data.stream()
                .sorted(Comparator.comparing(
                        d -> d.getSortOrder() != null ? d.getSortOrder() : Integer.MAX_VALUE))
                .collect(Collectors.toList());

        // 构建 item 映射
        Map<Long, CustomizeTreeItem<T>> itemMap = new LinkedHashMap<>();
        for (T item : sortedData) {
            itemMap.put(item.getId(), new CustomizeTreeItem<>(item));
        }

        // 组装父子关系
        for (T item : sortedData) {
            CustomizeTreeItem<T> treeItem = itemMap.get(item.getId());
            Long parentId = item.getParentId();

            if (parentId == null || parentId == 0) {
                root.getChildren().add(treeItem);
            } else {
                CustomizeTreeItem<T> parentItem = itemMap.get(parentId);
                // Prevent circular reference (self-parenting)
                if (parentItem != null && parentItem != treeItem) {
                    parentItem.getChildren().add(treeItem);
                } else {
                    root.getChildren().add(treeItem);
                }
            }
        }
    }
}
