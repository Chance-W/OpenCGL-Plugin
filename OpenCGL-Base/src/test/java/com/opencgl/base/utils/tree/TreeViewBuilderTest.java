package com.opencgl.base.utils.tree;

import com.opencgl.base.model.BaseDataDto;
import com.opencgl.base.service.TreeOperateService;
import com.opencgl.base.view.CustomizeTreeItem;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TreeViewBuilderTest {
    @Test void laterPagesDoNotUndoUserCollapseOfSelectedBranch() throws Exception {
        fx(() -> {
            var root = new TreeItem<>("root"); root.setExpanded(true);
            var folder = new TreeItem<>("folder"); folder.setExpanded(true);
            var leaf = new TreeItem<>("leaf"); folder.getChildren().add(leaf); root.getChildren().add(folder);
            var tree = new TreeView<>(root); tree.getSelectionModel().select(leaf);
            var state = TreeViewState.capture(tree, TreeItem::getValue);
            folder.setExpanded(false);
            state.withSelection("folder").withExpansion("folder", false).restore(tree);
            assertFalse(folder.isExpanded());
            assertSame(folder, tree.getSelectionModel().getSelectedItem());
        });
    }
    @BeforeAll static void toolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); }
        catch (IllegalStateException startedAlready) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    static void fx(Runnable assertions) throws Exception {
        FutureTask<Void> task = new FutureTask<>(assertions, null);
        Platform.runLater(task);
        task.get(15, TimeUnit.SECONDS);
    }

    static BaseDataDto dto(long id, long parent, String name, Boolean leaf) {
        BaseDataDto value = new BaseDataDto();
        value.setId(id); value.setParentId(parent); value.setName(name); value.setIsLeaf(leaf);
        return value;
    }

    static class Data implements TreeOperateService<BaseDataDto> {
        final List<BaseDataDto> rows = new ArrayList<>(List.of(
                dto(1, 0, "目录", false), dto(2, 1, "请求", true), dto(3, 0, "另一个目录", false)));
        final AtomicInteger displayed = new AtomicInteger();
        public List<BaseDataDto> queryAll() { return new ArrayList<>(rows); }
        public void changeToDisplay(BaseDataDto item) { displayed.incrementAndGet(); }
        public CustomizeTreeItem<BaseDataDto> add(BaseDataDto item) { rows.add(item); return new CustomizeTreeItem<>(item); }
        public CustomizeTreeItem<BaseDataDto> importData(BaseDataDto item) { return add(item); }
        public CustomizeTreeItem<BaseDataDto> delete(BaseDataDto item) { rows.remove(item); return new CustomizeTreeItem<>(item); }
        public CustomizeTreeItem<BaseDataDto> update(BaseDataDto item) { return new CustomizeTreeItem<>(item); }
    }

    record View(VBox box, TreeView<BaseDataDto> tree, TextField search, Data data) {}
    @SuppressWarnings("unchecked")
    static View view(Data data) {
        VBox box = new TreeViewBuilder<BaseDataDto>().service(data).dataType(BaseDataDto.class)
                .enableSearch().enableToolbar(true).build();
        TreeView<BaseDataDto> tree = (TreeView<BaseDataDto>) box.getChildren().stream()
                .filter(TreeView.class::isInstance).findFirst().orElseThrow();
        TextField search = (TextField) box.getChildren().stream()
                .filter(TextField.class::isInstance).findFirst().orElseThrow();
        return new View(box, tree, search, data);
    }

    @Test void nullableLeafFlagFallsBackWithoutBreakingExplicitEmptyFolders() {
        var unknown = new CustomizeTreeItem<>(dto(1, 0, "legacy", null));
        assertTrue(unknown.isLeaf());
        unknown.getChildren().add(new CustomizeTreeItem<>(dto(2, 1, "child", true)));
        assertFalse(unknown.isLeaf());
        assertFalse(new CustomizeTreeItem<>(dto(3, 0, "empty folder", false)).isLeaf());
    }

    @Test void clearSearchRestoresExpandedAndSelectedIdsWithoutReloadingDetails() throws Exception {
        fx(() -> {
            View v = view(new Data());
            var folder = v.tree.getRoot().getChildren().getFirst();
            folder.setExpanded(true);
            v.tree.getSelectionModel().select(folder.getChildren().getFirst());
            int calls = v.data.displayed.get();
            v.search.setText("不存在");
            assertTrue(v.tree.getRoot().getChildren().isEmpty());
            v.search.clear();
            assertTrue(v.tree.getRoot().getChildren().getFirst().isExpanded());
            assertNotNull(v.tree.getSelectionModel().getSelectedItem());
            assertEquals(2L, v.tree.getSelectionModel().getSelectedItem().getValue().getId());
            assertEquals(calls, v.data.displayed.get(), "filtering is not a business selection");
        });
    }

    @Test void searchIncludesNodesAddedAfterInitialBuildAndDoesNotResurrectDeletedNodes() throws Exception {
        fx(() -> {
            View v = view(new Data());
            var added = dto(4, 1, "新增中文", true);
            v.tree.getRoot().getChildren().getFirst().getChildren().add(v.data.add(added));
            v.search.setText("新增");
            assertEquals(1, v.tree.getRoot().getChildren().size());
            assertEquals(4L, v.tree.getRoot().getChildren().getFirst().getChildren().getFirst().getValue().getId());
            v.data.delete(added);
            v.tree.getRoot().getChildren().getFirst().getChildren().clear();
            v.search.clear();
            assertEquals(List.of(2L), v.tree.getRoot().getChildren().getFirst().getChildren().stream()
                    .map(i -> i.getValue().getId()).toList());
        });
    }

    @Test void locateWorksFromKeyboardAndOnlyLoadsOnce() throws Exception {
        fx(() -> {
            Data data = new Data();
            VBox box = new TreeViewBuilder<BaseDataDto>().service(data).dataType(BaseDataDto.class)
                    .enableToolbar(true).locateTargetSupplier(() -> data.rows.get(1)).build();
            Button locate = (Button) ((HBox) box.getChildren().getFirst()).getChildren().get(3);
            locate.fire();
            assertEquals(1, data.displayed.get());
            locate.fire();
            assertEquals(1, data.displayed.get(), "locating the current item must not reload it");
        });
    }

    @Test void parentMatchIncludesAllLoadedDescendantsAndKeepsBusinessLeaf() throws Exception {
        fx(() -> {
            View v = view(new Data());
            v.search.setText("  目录  ");
            var folder = v.tree.getRoot().getChildren().getFirst();
            assertFalse(folder.isLeaf());
            folder.setExpanded(false); folder.setExpanded(true);
            assertEquals(1, folder.getChildren().size());
            assertTrue(folder.getChildren().getFirst().isLeaf());
        });
    }

    @Test void malformedRowsDoNotCreateCyclesOrDuplicateTreeItems() throws Exception {
        fx(() -> {
            Data data = new Data();
            data.rows.clear();
            data.rows.addAll(List.of(dto(1, 2, "a", false), dto(2, 1, "b", false),
                    dto(3, 0, "first", true), dto(3, 0, "duplicate", true)));
            View v = view(data);
            var ids = TreeViewState.loadedItems(v.tree.getRoot()).stream()
                    .map(i -> i.getValue().getId()).toList();
            assertEquals(4, ids.size());
            assertEquals(4, new HashSet<>(ids).size());
        });
    }

    @Test void reusedCellsOfferFullNamesAndCleanEmptyTooltips() throws Exception {
        fx(() -> {
            View v = view(new Data());
            TreeCell<BaseDataDto> cell = v.tree.getCellFactory().call(v.tree);
            cell.updateTreeView(v.tree);
            cell.updateIndex(1);
            cell.fireEvent(new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_ENTERED,
                    0, 0, 0, 0, javafx.scene.input.MouseButton.NONE, 0, false, false, false, false,
                    false, false, false, false, false, false, null));
            assertNotNull(cell.getTooltip());
            assertEquals("目录", cell.getTooltip().getText());
            cell.updateIndex(-1);
            assertNull(cell.getTooltip());
        });
    }

    @Test void turkishLocaleDoesNotChangeAsciiSearchAndParentMatchKeepsSiblings() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var rows = List.of(dto(2, 1, "INPUT child", true), dto(1, 0, "INPUT", false),
                    dto(3, 1, "sibling", true));
            assertEquals(List.of(2L, 1L, 3L), TreeDataFilter.filter(rows, "input").stream()
                    .map(BaseDataDto::getId).toList());
        } finally { Locale.setDefault(original); }
    }

    @Test void replacingCollectionViewPreservesStateWithoutReloadingCurrentRequest() throws Exception {
        fx(() -> {
            var manager = new com.opencgl.base.view.RequestManagerView();
            Data data = new Data(); View first = view(data);
            manager.setCollectionView(first.box);
            var folder = first.tree.getRoot().getChildren().getFirst(); folder.setExpanded(true);
            first.tree.getSelectionModel().select(folder.getChildren().getFirst());
            int callbacks = data.displayed.get();
            View replacement = view(data); manager.setCollectionView(replacement.box);
            assertNotNull(replacement.tree.getSelectionModel().getSelectedItem());
            assertEquals(2L, replacement.tree.getSelectionModel().getSelectedItem().getValue().getId());
            assertTrue(replacement.tree.getRoot().getChildren().getFirst().isExpanded());
            assertEquals(callbacks, data.displayed.get());
        });
    }

    @Test void selectionMadeInSearchRemainsSelectedWhenSearchClears() throws Exception {
        fx(() -> {
            View v = view(new Data());
            v.search.setText("请求");
            var result = v.tree.getRoot().getChildren().getFirst().getChildren().getFirst();
            v.tree.getSelectionModel().select(result);
            v.search.clear();
            assertNotNull(v.tree.getSelectionModel().getSelectedItem());
            assertEquals(2L, v.tree.getSelectionModel().getSelectedItem().getValue().getId());
        });
    }

    @Test void refreshReloadsRepositoryAndKeepsSelectionWithoutDuplicateBusinessAction() throws Exception {
        fx(() -> {
            Data data = new Data();
            var builder = new TreeViewBuilder<BaseDataDto>().service(data).dataType(BaseDataDto.class).enableSearch();
            VBox box = builder.build();
            @SuppressWarnings("unchecked") var tree = (TreeView<BaseDataDto>) box.getChildren().get(1);
            tree.getRoot().getChildren().getFirst().setExpanded(true);
            tree.getSelectionModel().select(tree.getRoot().getChildren().getFirst().getChildren().getFirst());
            data.rows.add(dto(4, 1, "另一个请求", true));
            builder.refresh();
            assertEquals(2, tree.getRoot().getChildren().getFirst().getChildren().size());
            assertEquals(2L, tree.getSelectionModel().getSelectedItem().getValue().getId());
            assertEquals(1, data.displayed.get());
            data.rows.removeIf(i -> i.getId() == 2L);
            builder.refresh();
            assertNull(tree.getSelectionModel().getSelectedItem());
        });
    }
}
