package com.opencgl.plugin.zookeeper.controller;

import com.opencgl.base.service.TreeOperateService;
import com.opencgl.base.utils.DialogUtil;
import com.opencgl.base.utils.tree.TreeViewBuilder;
import com.opencgl.base.view.CustomizeTreeItem;
import com.opencgl.base.view.RequestManagerView;
import com.opencgl.plugin.zookeeper.i18n.I18N;
import com.opencgl.plugin.zookeeper.service.ZkConnection;
import com.opencgl.plugin.zookeeper.service.ZkConnectionRepository;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import java.util.*;
import java.util.function.Consumer;

/** Dubbo's shared collection tree, deliberately separate from the live znode tree. */
public final class ZkConnectionsPane extends RequestManagerView implements TreeOperateService<ZkConnection> {
    private final ZkConnectionRepository repository;
    private final Consumer<ZkConnection> select;
    private final Runnable connect;
    private TreeView<ZkConnection> tree;
    public TreeView<ZkConnection> getTree() { return tree; }

    public ZkConnectionsPane(ZkConnectionRepository repository, Consumer<ZkConnection> select, Runnable connect) {
        this.repository = repository; this.select = select; this.connect = connect;
        getTabPane().getTabs().remove(1);
        setMinWidth(200); setPrefWidth(250);
        rebuild(null);
    }

    private void rebuild(Long selectedId) {
        Set<Long> expanded = new HashSet<>();
        if (tree != null) collectExpanded(tree.getRoot(), expanded);
        VBox content = new TreeViewBuilder<ZkConnection>().service(this).dataType(ZkConnection.class)
                .enableDragDrop(false).editable(false).enableSearch().searchPrompt(I18N.get("connection.search"))
                .onSelect(select).contextMenuFactory(this::menu).onTreeCreated(t -> tree = t).build();
        tree.setId("connectionTree");
        tree.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() != MouseButton.PRIMARY || e.getClickCount() != 2) return;
            javafx.scene.Node target = e.getTarget() instanceof javafx.scene.Node n ? n : null;
            while (target != null && !(target instanceof TreeCell<?>)) target = target.getParent();
            if (target instanceof TreeCell<?> cell && !cell.isEmpty()
                    && cell.getItem() instanceof ZkConnection n && Boolean.TRUE.equals(n.getIsLeaf())) {
                select.accept(n); connect.run(); e.consume();
            }
        });
        var add = new MenuButton("+"); add.setTooltip(new Tooltip(I18N.get("connection.add")));
        add.getItems().addAll(action("connection.add_group", () -> create(false)), action("connection.add", () -> create(true)));
        var copy = button("connection.copy", () -> { var n = selected(); if (isConnection(n)) rebuild(repository.copy(n).getId()); });
        var delete = button("connection.delete", this::deleteSelected);
        var toolbar = new HBox(5, add, copy, delete);
        content.getChildren().add(0, toolbar);
        var hint = new Label(I18N.get("connection.hint")); hint.setWrapText(true);
        content.getChildren().add(hint);
        VBox.setVgrow(content, Priority.ALWAYS);
        setCollectionView(content);
        restore(tree.getRoot(), expanded, selectedId);
        tree.getRoot().setExpanded(true);
    }
    private Button button(String key, Runnable action) {
        var b = new Button(); b.textProperty().bind(I18N.getBinding(key)); b.setOnAction(e -> guard(action)); return b;
    }
    private MenuItem action(String key, Runnable action) {
        var item = new MenuItem(); item.textProperty().bind(I18N.getBinding(key)); item.setOnAction(e -> guard(action)); return item;
    }
    private void guard(Runnable action) {
        try { action.run(); } catch (RuntimeException e) { DialogUtil.showErrorInfo(e.getMessage()); }
    }
    private ContextMenu menu(ZkConnection n) {
        var menu = new ContextMenu();
        menu.getItems().addAll(action("connection.add_group", () -> create(false)), action("connection.add", () -> create(true)));
        if (n.getId() != null && n.getId() != 0) {
            menu.getItems().add(action("connection.rename", () -> {
                String name = DialogUtil.show(n.getName()); if (name == null || name.isBlank()) return;
                var updated = n.snapshot(); updated.setName(name); repository.save(updated); rebuild(updated.getId());
            }));
            if (isConnection(n)) menu.getItems().addAll(action("button.connect", () -> { select.accept(n); connect.run(); }),
                    action("connection.copy", () -> rebuild(repository.copy(n).getId())));
            menu.getItems().add(action("connection.delete", this::deleteSelected));
        }
        return menu;
    }
    private boolean isConnection(ZkConnection n) { return n != null && Boolean.TRUE.equals(n.getIsLeaf()); }
    private ZkConnection selected() {
        var item = tree.getSelectionModel().getSelectedItem(); return item == null ? null : item.getValue();
    }
    private long parentId() {
        var n = selected(); return n == null ? 0 : isConnection(n) ? n.getParentId() : n.getId();
    }
    private void create(boolean leaf) {
        String name = DialogUtil.show(); if (name == null || name.isBlank()) return;
        var n = repository.create(parentId(), leaf, name); rebuild(n.getId());
    }
    private void deleteSelected() {
        var n = selected(); if (n == null || n.getId() == 0) return;
        if (!Boolean.TRUE.equals(DialogUtil.showConfirmDialog(I18N.get("connection.delete"), I18N.get("connection.delete_confirm"), this))) return;
        repository.delete(n.getId()); select.accept(null); rebuild(null);
    }
    public void saveProfile(ZkConnection profile) { repository.save(profile); rebuild(profile.getId()); }
    private void collectExpanded(TreeItem<ZkConnection> n, Set<Long> expanded) {
        if (n.isExpanded()) expanded.add(n.getValue().getId()); n.getChildren().forEach(c -> collectExpanded(c, expanded));
    }
    private void restore(TreeItem<ZkConnection> n, Set<Long> expanded, Long id) {
        n.setExpanded(expanded.contains(n.getValue().getId()));
        if (Objects.equals(n.getValue().getId(), id)) {
            for (var p = n.getParent(); p != null; p = p.getParent()) p.setExpanded(true);
            tree.getSelectionModel().select(n);
        }
        n.getChildren().forEach(c -> restore(c, expanded, id));
    }
    @Override public List<ZkConnection> queryAll() { return repository.load(); }
    @Override public void changeToDisplay(ZkConnection n) { /* onSelect handles folders and connections uniformly. */ }
    @Override public boolean supportDragDrop() { return false; }
    @Override public CustomizeTreeItem<ZkConnection> add(ZkConnection n) { repository.save(n); return new CustomizeTreeItem<>(n); }
    @Override public CustomizeTreeItem<ZkConnection> update(ZkConnection n) { return add(n); }
    @Override public CustomizeTreeItem<ZkConnection> importData(ZkConnection n) { throw new UnsupportedOperationException(); }
    @Override public CustomizeTreeItem<ZkConnection> delete(ZkConnection n) { repository.delete(n.getId()); return new CustomizeTreeItem<>(n); }
}
