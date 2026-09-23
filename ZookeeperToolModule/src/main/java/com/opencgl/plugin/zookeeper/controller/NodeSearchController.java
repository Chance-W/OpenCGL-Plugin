package com.opencgl.plugin.zookeeper.controller;

import com.opencgl.plugin.zookeeper.i18n.I18N;
import com.opencgl.plugin.zookeeper.service.NodeSearch;
import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.util.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Owns search UI state on the FX thread; only directory traversal runs on its worker. */
public final class NodeSearchController implements AutoCloseable {
    private final TreeView<String> tree;
    private final TextField input;
    private final Button all, cancel;
    private final Label status;
    private final ProgressIndicator progress;
    private final Supplier<NodeSearch.ChildrenReader> reader;
    private final Consumer<TreeItem<String>> open;
    private final PauseTransition debounce = new PauseTransition(Duration.millis(180));
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        var thread = new Thread(r, "opencgl-zookeeper-search"); thread.setDaemon(true); return thread;
    });
    private TreeItem<String> browseRoot, previousSelection;
    private Task<NodeSearch.Result> active;
    private final ExecutorService expandExecutor = Executors.newSingleThreadExecutor(r -> {
        var thread = new Thread(r, "opencgl-zookeeper-search-expand"); thread.setDaemon(true); return thread;
    });
    private final Map<TreeItem<String>, Task<List<String>>> expanding = new IdentityHashMap<>();
    private final Set<TreeItem<String>> loaded = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean rebuilding, closed;

    public NodeSearchController(TreeView<String> tree, TextField input, Button all, Button cancel,
                                Button clear, Label status, ProgressIndicator progress,
                                Supplier<NodeSearch.ChildrenReader> reader, Consumer<TreeItem<String>> open) {
        this.tree = tree; this.input = input; this.all = all; this.cancel = cancel;
        com.opencgl.base.utils.tree.TreeViewPresentation.install(tree);
        this.status = status; this.progress = progress; this.reader = reader; this.open = open;
        input.promptTextProperty().bind(I18N.getBinding("search.placeholder"));
        all.textProperty().bind(I18N.getBinding("search.all"));
        cancel.textProperty().bind(I18N.getBinding("search.cancel"));
        clear.textProperty().bind(I18N.getBinding("search.clear"));
        setBusy(false);
        status.setText(I18N.get("search.hint"));
        debounce.setOnFinished(e -> filterLoaded());
        input.textProperty().addListener((o, a, b) -> {
            if (rebuilding || closed) return;
            stopTask();
            stopExpansions();
            if (b.isBlank()) clearSearch();
            else debounce.playFromStart();
        });
        input.setOnAction(e -> { debounce.stop(); filterLoaded(); });
        clear.setOnAction(e -> clearSearch());
        cancel.setOnAction(e -> {
            stopTask(); status.setText(I18N.get("search.cancelled"));
        });
        all.setOnAction(e -> searchAll());
        tree.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (!closed && !rebuilding && isSearching() && b != null && !(b instanceof LoadingItem)) open.accept(b);
        });
    }

    public boolean isSearching() { return browseRoot != null; }
    public TreeItem<String> getBrowseRoot() { return isSearching() ? browseRoot : tree.getRoot(); }

    private void rememberTree() {
        if (!isSearching()) {
            browseRoot = tree.getRoot(); previousSelection = tree.getSelectionModel().getSelectedItem();
        }
    }

    private void showPaths(java.util.List<String> paths) {
        stopExpansions();
        rebuilding = true;
        try {
            var root = NodeSearchTree.fromPaths(paths);
            if (!paths.isEmpty()) {
                var pending = new ArrayDeque<TreeItem<String>>(); pending.add(root);
                while (!pending.isEmpty()) {
                    var item = pending.removeFirst(); pending.addAll(item.getChildren());
                    prepareExpansion(item);
                }
            }
            tree.setRoot(root);
        }
        finally { rebuilding = false; }
    }

    private static final class LoadingItem extends TreeItem<String> {
        private LoadingItem() { super(I18N.get("label.loading")); }
    }

    private void prepareExpansion(TreeItem<String> item) {
        if (item.getChildren().isEmpty()) {
            item.setExpanded(false);
            item.getChildren().add(new LoadingItem());
        }
        item.expandedProperty().addListener((o, a, b) -> { if (b) expand(item); });
    }

    /** Expand the result itself, not a detached node in the saved browsing tree. */
    public void expand(TreeItem<String> item) {
        if (closed || !isSearching() || item == null || item instanceof LoadingItem
                || loaded.contains(item) || expanding.containsKey(item)) return;
        var source = reader.get();
        if (source == null) { status.setText(I18N.get("message.zk_not_connected")); return; }
        var root = tree.getRoot();
        String path = NodeSearchTree.path(item);
        Task<List<String>> task = new Task<>() {
            @Override protected List<String> call() throws Exception { return source.children(path); }
        };
        expanding.put(item, task);
        task.setOnSucceeded(e -> {
            if (closed || root != tree.getRoot() || expanding.get(item) != task) return;
            expanding.remove(item); loaded.add(item);
            var existing = new HashMap<String, TreeItem<String>>();
            for (var child : item.getChildren()) {
                if (!(child instanceof LoadingItem)) existing.put(child.getValue(), child);
            }
            var children = new ArrayList<TreeItem<String>>();
            for (String name : task.getValue()) {
                var child = existing.get(name);
                if (child == null) { child = new TreeItem<>(name); prepareExpansion(child); }
                children.add(child);
            }
            item.getChildren().setAll(children);
            item.setExpanded(true);
        });
        task.setOnFailed(e -> {
            if (closed || root != tree.getRoot() || expanding.get(item) != task) return;
            expanding.remove(item);
            item.setExpanded(false);
            status.setText(I18N.get("search.expand_failed", path, task.getException().getClass().getSimpleName()));
        });
        expandExecutor.execute(task);
    }

    private void stopExpansions() {
        var tasks = List.copyOf(expanding.values()); expanding.clear(); loaded.clear();
        tasks.forEach(task -> task.cancel(true));
    }

    private void filterLoaded() {
        if (closed || input.getText().isBlank()) return;
        stopTask(); rememberTree();
        var paths = NodeSearchTree.loadedPaths(browseRoot, I18N.get("label.loading")).stream()
                .filter(path -> NodeSearch.matches(path, input.getText())).toList();
        showPaths(paths);
        status.setText(I18N.get("search.local_result", paths.size()));
    }

    private void searchAll() {
        debounce.stop(); stopTask();
        if (closed) return;
        String query = input.getText().trim();
        if (query.isEmpty()) { status.setText(I18N.get("search.enter_query")); return; }
        var source = reader.get();
        if (source == null) { status.setText(I18N.get("message.zk_not_connected")); return; }
        filterLoaded();
        Task<NodeSearch.Result> task = new Task<>() {
            @Override protected NodeSearch.Result call() throws Exception {
                final long[] lastUpdate = {0};
                return NodeSearch.scan(source, query, this::isCancelled, p -> {
                    long now = System.nanoTime();
                    if (now - lastUpdate[0] >= 100_000_000L) {
                        lastUpdate[0] = now;
                        updateMessage(I18N.get("search.progress", p.visited(), p.matched(), p.skipped()));
                    }
                });
            }
        };
        active = task; setBusy(true);
        status.setText(I18N.get("search.progress", 0, 0, 0));
        task.messageProperty().addListener((o, a, b) -> { if (active == task) status.setText(b); });
        task.setOnSucceeded(e -> {
            if (active != task || closed) return;
            var result = task.getValue(); showPaths(result.matches());
            status.setText(I18N.get(result.truncated() ? "search.limited" : "search.complete",
                    result.visited(), result.matches().size(), result.skipped()));
            active = null; setBusy(false);
        });
        task.setOnFailed(e -> {
            if (active != task || closed) return;
            status.setText(I18N.get("search.failed", task.getException().getClass().getSimpleName()));
            active = null; setBusy(false);
        });
        executor.execute(task);
    }

    private void stopTask() {
        var task = active; active = null;
        if (task != null) task.cancel(true);
        setBusy(false);
    }

    private void setBusy(boolean busy) {
        all.setDisable(busy); cancel.setDisable(!busy);
        progress.setVisible(busy); progress.setManaged(busy);
    }

    private void clearSearch() {
        var selection = previousSelection;
        reset();
        if (selection != null) open.accept(selection);
    }

    /** Called before refresh/disconnect so late search results cannot restore old state. */
    public void reset() {
        debounce.stop(); stopTask(); stopExpansions();
        rebuilding = true;
        try {
            if (isSearching()) {
                tree.setRoot(browseRoot);
                tree.getSelectionModel().select(previousSelection);
                int row = tree.getRow(previousSelection);
                if (row >= 0) tree.scrollTo(row);
            }
            browseRoot = null; previousSelection = null;
            if (!input.getText().isEmpty()) input.clear();
            status.setText(I18N.get("search.hint"));
        } finally { rebuilding = false; }
    }

    @Override public void close() {
        closed = true; debounce.stop(); stopTask(); stopExpansions();
        executor.shutdownNow(); expandExecutor.shutdownNow();
    }
}
