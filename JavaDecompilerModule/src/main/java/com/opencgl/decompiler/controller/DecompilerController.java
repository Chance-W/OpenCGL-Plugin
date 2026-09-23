package com.opencgl.decompiler.controller;

import com.opencgl.decompiler.i18n.I18N;
import com.opencgl.decompiler.model.ClassNode;
import com.opencgl.decompiler.model.DecompileResult;
import com.opencgl.decompiler.service.DecompilerService;
import com.opencgl.decompiler.service.ClassTreeSearch;
import com.opencgl.base.utils.tree.TreeViewState;
import com.opencgl.decompiler.service.JarLoaderService;
import com.opencgl.decompiler.service.SyntaxHighlightService;
import com.opencgl.decompiler.service.SymbolIndexService;
import com.opencgl.decompiler.service.SymbolNavigationService;
import com.opencgl.decompiler.views.DecompilerView;
import javafx.application.Platform;
import javafx.fxml.Initializable;
import javafx.scene.control.TreeItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TreeCell;
import javafx.scene.Node;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ResourceBundle;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.HashMap;

/**
 * 反编译器控制器
 */
public class DecompilerController extends DecompilerView implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(DecompilerController.class);

    private DecompilerService decompilerService;
    private JarLoaderService jarLoaderService;
    private SyntaxHighlightService syntaxHighlightService;
    private SymbolIndexService symbolIndexService;
    private TreeItem<ClassNode> fullTreeRoot; // 保存完整树用于搜索
    private TreeViewState<ClassNode, java.util.List<String>> beforeTreeSearch;
    private boolean rebuildingFileTree;
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "opencgl-decompiler");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean disposed;
    private final Map<String, TreeItem<ClassNode>> archiveTrees = new LinkedHashMap<>();
    private TreeItem<ClassNode> workspaceRoot;
    private final Map<String, CodeArea> openEditors = new HashMap<>();
    private final Map<String, Tab> openTabs = new HashMap<>();
    private String lastSearchQuery = "";
    private int lastSearchPosition = -1;
    private final ExecutorService navigationExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "opencgl-symbol-navigation"); thread.setDaemon(true); return thread;
    });
    private SymbolNavigationService navigationService;
    private final Map<String, ClassNode> indexedNodes = new HashMap<>();
    private final Map<CodeArea, EditorNavigation> editorNavigation = new HashMap<>();
    private volatile Map<String, String> sourceCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, ClassNode> editorNodes = new HashMap<>();
    private final java.util.Deque<NavigationLocation> backLocations = new java.util.ArrayDeque<>();
    private final java.util.Deque<NavigationLocation> forwardLocations = new java.util.ArrayDeque<>();
    private record NavigationLocation(String key, int start, int end) {}
    private long indexGeneration, navigationGeneration, workspaceGeneration;
    private java.util.concurrent.Future<?> indexTask;
    private final javafx.scene.control.ContextMenu navigationChoices = new javafx.scene.control.ContextMenu();

    private CompletableFuture<Void> runAsync(Runnable action) {
        return CompletableFuture.runAsync(() -> {
            if (!disposed) action.run();
        }, executor);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 延迟初始化服务（避免ClassLoader问题）
        decompilerService = new DecompilerService();
        jarLoaderService = new JarLoaderService();
        syntaxHighlightService = new SyntaxHighlightService();
        symbolIndexService = new SymbolIndexService();
        
        setupUI();
        bindEvents();
        initI18n();
        setStatus(I18N.get("msg.ready"));
    }

    private void initI18n() {
        openFileButton.textProperty().bind(I18N.getBinding("button.open_file"));
        openFolderButton.textProperty().bind(I18N.getBinding("button.open_folder"));
        exportButton.textProperty().bind(I18N.getBinding("button.export"));
        exportAllButton.textProperty().bind(I18N.getBinding("button.export_all"));
        searchField.promptTextProperty().bind(I18N.getBinding("prompt.search_class"));
    }

    private void setupUI() {
        com.opencgl.base.utils.tree.TreeViewPresentation.install(fileTreeView);
        rootPane.getStylesheets().add(getClass().getResource("/com/opencgl/decompiler/styles/navigation.css").toExternalForm());
        // 初始化代码区域
        // 编辑器在首次打开 class 时按 Tab 创建，避免多个 class 共享同一个编辑区。
        
        // 初始化树视图
        fileTreeView.setShowRoot(true);
        fileTreeView.setContextMenu(new javafx.scene.control.ContextMenu());
        
        // 初始禁用导出按钮
        exportButton.setDisable(true);
        exportAllButton.setDisable(true);
    }

    private void bindEvents() {
        openFileButton.setOnAction(e -> onOpenFile());
        openFolderButton.setOnAction(e -> onOpenFolder());
        exportButton.setOnAction(e -> onExport());
        exportAllButton.setOnAction(e -> onExportAll());
        navigationBackButton.setOnAction(e -> navigateHistory(true));
        navigationForwardButton.setOnAction(e -> navigateHistory(false));
        
        // 树节点选择事件
        fileTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (rebuildingFileTree) return;
            if (newVal != null && beforeTreeSearch != null)
                beforeTreeSearch = beforeTreeSearch.withSelection(treeNodeKey(newVal));
            if (newVal != null && newVal.getValue().isClass()) {
                decompileClass(newVal.getValue());
            }
        });
        
        // 双击jar文件展开
        fileTreeView.setOnMouseClicked(event -> {
            if (event.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                Node target = (Node) event.getTarget();
                while (target != null && !(target instanceof TreeCell<?>)) target = target.getParent();
                if (target instanceof TreeCell<?> cell && cell.getTreeItem() != null) fileTreeView.getSelectionModel().select((TreeItem<ClassNode>) cell.getTreeItem());
                TreeItem<ClassNode> item = fileTreeView.getSelectionModel().getSelectedItem();
                if (item != null && JarLoaderService.isArchive(new File(item.getValue().getFullPath()))) {
                    javafx.scene.control.MenuItem remove = new javafx.scene.control.MenuItem("移除 JAR");
                    remove.setOnAction(e -> removeArchiveTree(item));
                    fileTreeView.setContextMenu(new javafx.scene.control.ContextMenu(remove));
                } else fileTreeView.setContextMenu(new javafx.scene.control.ContextMenu());
                return;
            }
            if (event.getClickCount() == 2) {
                TreeItem<ClassNode> item = fileTreeView.getSelectionModel().getSelectedItem();
                if (item != null && JarLoaderService.isArchive(new File(item.getValue().getFullPath()))) {
                    onExpandJar(item);
                }
            }
        });
        
        // 搜索功能
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterTree(newVal);
        });
        codeSearchField.setOnAction(e -> searchInCurrentEditor());
        codeSearchNextButton.setOnAction(e -> searchCurrent(true));
        codeSearchPrevButton.setOnAction(e -> searchCurrent(false));
        codeTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            navigationGeneration++;
            navigationChoices.hide();
            editorNavigation.values().forEach(EditorNavigation::clear);
            if (codeSearchBar.getParent() instanceof javafx.scene.layout.StackPane previous) {
                previous.getChildren().remove(codeSearchBar);
            }
            codeArea = null;
            if (newTab != null && newTab.getContent() instanceof javafx.scene.layout.StackPane content
                    && newTab.getUserData() instanceof CodeArea editor) {
                codeArea = editor; lastSearchPosition = -1;
                content.getChildren().add(codeSearchBar);
            }
        });
        rootPane.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.isShortcutDown() && e.getCode() == javafx.scene.input.KeyCode.F) {
                if (codeArea == null) return;
                if (codeArea != null && codeArea.getSelectedText() != null && !codeArea.getSelectedText().isBlank()) {
                    codeSearchField.setText(codeArea.getSelectedText());
                }
                codeSearchField.requestFocus(); codeSearchField.selectAll(); e.consume();
            } else if (e.getCode() == javafx.scene.input.KeyCode.ENTER && e.isShiftDown() && codeSearchField.isFocused()) {
                searchCurrent(false); e.consume();
            } else if (e.isAltDown() && e.getCode() == javafx.scene.input.KeyCode.LEFT) {
                navigateHistory(true); e.consume();
            } else if (e.isAltDown() && e.getCode() == javafx.scene.input.KeyCode.RIGHT) {
                navigateHistory(false); e.consume();
            }
        });
    }

    private void onOpenFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18N.get("dialog.select_file"));
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter(I18N.get("filter.java_files"), "*.jar", "*.class", "*.zip"),
            new FileChooser.ExtensionFilter(I18N.get("filter.all_files"), "*.*")
        );
        
        List<File> files = fileChooser.showOpenMultipleDialog(rootPane.getScene().getWindow());
        if (files != null) files.forEach(this::loadFile);
    }

    private void onOpenFolder() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle(I18N.get("dialog.select_folder"));
        
        File dir = dirChooser.showDialog(rootPane.getScene().getWindow());
        if (dir != null) {
            loadDirectory(dir);
        }
    }

    private void loadFile(File file) {
        setStatus(I18N.get("msg.loading_file", file.getName()));
        long workspace = workspaceGeneration;
        
        runAsync(() -> {
            try {
                if (JarLoaderService.isArchive(file)) {
                    TreeItem<ClassNode> archive = jarLoaderService.loadJarFile(file);
                    Platform.runLater(() -> { if (!disposed && workspace == workspaceGeneration) addArchiveTree(file, archive); });
                } else {
                    TreeItem<ClassNode> root = jarLoaderService.loadClassFile(file);
                    Platform.runLater(() -> {
                        if (disposed || workspace != workspaceGeneration) return;
                        if (workspaceRoot == null) { workspaceRoot = new TreeItem<>(new ClassNode("已加载文件", "", false, true)); workspaceRoot.setExpanded(true); }
                        workspaceRoot.getChildren().add(root);
                        fileTreeView.setRoot(workspaceRoot); fullTreeRoot = workspaceRoot;
                        setFileInfo(file); exportAllButton.setDisable(false); startSymbolIndex();
                    });
                }
                Platform.runLater(() -> {
                    setStatus(I18N.get("msg.loaded_file", file.getName()));
                });
                
            } catch (IOException e) {
                logger.error("加载文件失败", e);
                Platform.runLater(() -> setStatus(I18N.get("msg.load_failed", e.getMessage())));
            }
        });
    }

    private void loadDirectory(File dir) {
        setStatus(I18N.get("msg.scanning_dir", dir.getName()));
        long workspace = ++workspaceGeneration;
        // 文件夹打开表示切换工作区，清理上一个工作区的归档列表，避免列表与树不一致。
        jarLoaderService.clearLoadedArchives();
        archiveTrees.clear();
        symbolIndexService.clear();
        workspaceRoot = new TreeItem<>(new ClassNode(dir.getName(), dir.getAbsolutePath(), false, true));
        workspaceRoot.setExpanded(true); fileTreeView.setRoot(workspaceRoot); fullTreeRoot = workspaceRoot;
        TreeItem<ClassNode> directoryRoot = workspaceRoot;
        setFileInfo(dir); startSymbolIndex();
        
        runAsync(() -> {
            try {
                List<File> archives;
                try (var stream = Files.list(dir.toPath())) {
                    archives = stream.filter(Files::isRegularFile).map(Path::toFile)
                            .filter(JarLoaderService::isArchive).toList();
                }
                for (File archive : archives) {
                    TreeItem<ClassNode> tree = jarLoaderService.loadJarFile(archive);
                    Platform.runLater(() -> { if (!disposed && workspace == workspaceGeneration) addArchiveTree(archive, tree); });
                }
                scanDirectory(dir.toPath(), directoryRoot, "");
                Platform.runLater(() -> {
                    if (disposed || workspace != workspaceGeneration) return;
                    startSymbolIndex();
                    setStatus(I18N.get("msg.scanned_dir", dir.getName()) + "（已加载 " + archives.size() + " 个 JAR）");
                });
                
            } catch (Exception e) {
                logger.error("扫描文件夹失败", e);
                Platform.runLater(() -> setStatus(I18N.get("msg.scan_failed", e.getMessage())));
            }
        });
    }

    private void scanDirectory(Path dir, TreeItem<ClassNode> parent, String basePath) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String fileName = file.getFileName().toString();
                if (fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".class")) {
                    String relativePath = dir.relativize(file).toString();
                    ClassNode node = new ClassNode(fileName, file.toString(), fileName.endsWith(".class"), false);
                    Platform.runLater(() -> parent.getChildren().add(new TreeItem<>(node)));
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void addArchiveTree(File file, TreeItem<ClassNode> tree) {
        jarLoaderService.addLoadedArchive(file);
        if (workspaceRoot == null) { workspaceRoot = new TreeItem<>(new ClassNode("已加载文件", "", false, true)); workspaceRoot.setExpanded(true); }
        archiveTrees.put(file.getAbsolutePath(), tree);
        workspaceRoot.getChildren().removeIf(item -> item.getValue().getSourcePath() != null && item.getValue().getSourcePath().equals(file.getAbsolutePath()));
        workspaceRoot.getChildren().add(tree);
        fileTreeView.setRoot(workspaceRoot); fullTreeRoot = workspaceRoot;
        exportAllButton.setDisable(false); setFileInfo(file);
        startSymbolIndex();
    }

    private void removeArchiveTree(TreeItem<ClassNode> item) {
        File file = new File(item.getValue().getSourcePath() == null ? item.getValue().getFullPath() : item.getValue().getSourcePath());
        jarLoaderService.removeLoadedArchive(file);
        archiveTrees.remove(file.getAbsolutePath());
        if (workspaceRoot != null) workspaceRoot.getChildren().remove(item);
        setStatus("已移除 " + file.getName());
        startSymbolIndex();
    }

    private void startSymbolIndex() {
        if (workspaceRoot == null || symbolIndexService == null) return;
        long generation = ++indexGeneration;
        navigationGeneration++;
        navigationService = null;
        indexedNodes.clear();
        sourceCache = new java.util.concurrent.ConcurrentHashMap<>();
        Map<String, String> cache = sourceCache;
        if (indexTask != null) indexTask.cancel(true);
        List<ClassNode> snapshot = SymbolIndexService.snapshot(workspaceRoot);
        Map<String, ClassNode> liveNodes = new HashMap<>();
        snapshot.forEach(node -> liveNodes.put(classKey(node), node));
        for (String key : List.copyOf(openTabs.keySet())) {
            if (liveNodes.get(key) != editorNodes.get(key)) closeEditor(key);
        }
        backLocations.removeIf(location -> !liveNodes.containsKey(location.key()));
        forwardLocations.removeIf(location -> !liveNodes.containsKey(location.key()));
        updateNavigationButtons();
        symbolIndexProgress.setVisible(true); symbolIndexProgress.setManaged(true);
        symbolIndexProgress.setProgress(0); symbolIndexLabel.setText("索引中…");
        indexTask = executor.submit(() -> {
            SymbolIndexService index = new SymbolIndexService();
            index.index(snapshot, (done, total) -> Platform.runLater(() -> {
                if (disposed || generation != indexGeneration) return;
                symbolIndexProgress.setProgress(total == 0 ? 1 : (double) done / total);
                symbolIndexLabel.setText(done == total ? "符号索引完成 " + done + "/" + total : "符号索引 " + done + "/" + total);
            }));
            if (Thread.currentThread().isInterrupted()) return;
            List<SymbolNavigationService.Source> sources = index.entries().stream().map(entry ->
                    new SymbolNavigationService.Source(classKey(entry.node()), entry.qualifiedName(),
                            () -> decompiledSource(entry.node(), cache))).toList();
            SymbolNavigationService service = new SymbolNavigationService(sources);
            Platform.runLater(() -> {
                if (disposed || generation != indexGeneration) return;
                symbolIndexService = index;
                index.entries().forEach(entry -> indexedNodes.put(classKey(entry.node()), entry.node()));
                navigationService = service;
            });
        });
    }

    private String classKey(ClassNode node) {
        return (node.getSourcePath() == null ? "" : node.getSourcePath()) + "!" + node.getFullPath();
    }

    private CodeArea createEditor(ClassNode node) {
        CodeArea editor = new CodeArea();
        editor.setEditable(false);
        editor.getStyleClass().add("code-area");
        editor.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12;");
        editor.setParagraphGraphicFactory(LineNumberFactory.get(editor));
        String css = getClass().getResource("/com/opencgl/decompiler/styles/java-highlighting.css").toExternalForm();
        editor.getStylesheets().add(css);
        javafx.scene.layout.StackPane content = new javafx.scene.layout.StackPane(editor);
        javafx.scene.layout.StackPane.setAlignment(codeSearchBar, javafx.geometry.Pos.TOP_RIGHT);
        javafx.scene.layout.StackPane.setMargin(codeSearchBar, new javafx.geometry.Insets(8, 18, 0, 0));
        Tab tab = new Tab(node.getName(), content);
        tab.setUserData(editor);
        tab.setClosable(true);
        String key = classKey(node);
        tab.setOnClosed(e -> closeEditor(key));
        openTabs.put(key, tab);
        openEditors.put(key, editor);
        editorNodes.put(key, node);
        codeTabPane.getTabs().add(tab);
        codeTabPane.getSelectionModel().select(tab);
        editorNavigation.put(editor, new EditorNavigation(editor,
                (offset, callback) -> resolveSymbol(editor, node, offset, false, callback),
                offset -> requestNavigation(editor, node, offset)));
        return editor;
    }

    private void closeEditor(String key) {
        Tab tab = openTabs.remove(key);
        CodeArea editor = openEditors.remove(key);
        editorNodes.remove(key);
        EditorNavigation navigation = editorNavigation.remove(editor);
        if (navigation != null) navigation.dispose();
        if (tab != null) codeTabPane.getTabs().remove(tab);
    }

    private void searchInCurrentEditor() {
        searchCurrent(true);
    }

    private void searchCurrent(boolean forward) {
        if (codeArea == null || codeSearchField.getText().isBlank()) return;
        String query = codeSearchField.getText();
        if (!query.equals(lastSearchQuery)) { lastSearchQuery = query; lastSearchPosition = forward ? -1 : codeArea.getLength(); }
        int found = forward ? codeArea.getText().indexOf(query, Math.min(lastSearchPosition + 1, codeArea.getLength()))
                : codeArea.getText().lastIndexOf(query, Math.max(0, lastSearchPosition - 1));
        if (found < 0) found = forward ? codeArea.getText().indexOf(query) : codeArea.getText().lastIndexOf(query);
        if (found >= 0) {
            lastSearchPosition = found; codeArea.selectRange(found, found + query.length());
            codeArea.showParagraphAtCenter(codeArea.getText().substring(0, found).split("\\n", -1).length - 1);
            codeSearchField.requestFocus();
        }
    }

    private void resolveSymbol(CodeArea editor, ClassNode current, int offset, boolean usages,
                               java.util.function.Consumer<SymbolNavigationService.Result> callback) {
        SymbolNavigationService service = navigationService;
        long generation = indexGeneration;
        if (service == null) {
            callback.accept(new SymbolNavigationService.Result(List.of(), "符号索引尚未完成，请稍后再试"));
            return;
        }
        navigationExecutor.submit(() -> {
            if (disposed) return;
            var result = service.resolve(classKey(current), offset, usages);
            Platform.runLater(() -> {
                if (!disposed && generation == indexGeneration && openEditors.get(classKey(current)) == editor) callback.accept(result);
            });
        });
    }

    private void requestNavigation(CodeArea editor, ClassNode current, int offset) {
        long request = ++navigationGeneration;
        NavigationLocation origin = new NavigationLocation(classKey(current), offset, offset);
        setStatus("正在解析符号…");
        resolveSymbol(editor, current, offset, true, result -> {
            if (request != navigationGeneration || codeArea != editor) return;
            setStatus(result.message());
            if (result.targets().isEmpty()) return;
            if (result.targets().size() == 1) {
                openNavigationTarget(result.targets().get(0), origin, true);
            } else {
                navigationChoices.getItems().clear();
                for (var target : result.targets()) {
                    var item = new javafx.scene.control.MenuItem(target.label());
                    item.setOnAction(e -> openNavigationTarget(target, origin, true));
                    navigationChoices.getItems().add(item);
                }
                navigationChoices.show(editor, javafx.geometry.Side.TOP, 20, 35);
            }
        });
    }

    private NavigationLocation currentLocation() {
        for (var entry : openEditors.entrySet()) {
            if (entry.getValue() == codeArea) return new NavigationLocation(entry.getKey(), codeArea.getSelection().getStart(), codeArea.getSelection().getEnd());
        }
        return null;
    }

    private void openNavigationTarget(SymbolNavigationService.Target target, NavigationLocation origin, boolean remember) {
        ClassNode node = indexedNodes.get(target.sourceId());
        SymbolNavigationService service = navigationService;
        if (node == null || service == null) { setStatus("目标类已从工作区移除"); return; }
        long request = ++navigationGeneration;
        navigationExecutor.submit(() -> {
            try {
                String source = service.sourceText(target.sourceId());
                var highlighting = syntaxHighlightService.computeHighlighting(source);
                Platform.runLater(() -> {
                    if (disposed || request != navigationGeneration || service != navigationService) return;
                    CodeArea editor = openEditors.get(target.sourceId());
                    if (editor == null) editor = createEditor(node);
                    if (!editor.getText().equals(source)) {
                        editor.replaceText(source); editor.setStyleSpans(0, highlighting);
                    }
                    codeTabPane.getSelectionModel().select(openTabs.get(target.sourceId()));
                    if (remember && origin != null) {
                        backLocations.push(origin); forwardLocations.clear();
                        while (backLocations.size() > 100) backLocations.removeLast();
                    }
                    editor.selectRange(Math.min(target.start(), editor.getLength()), Math.min(target.end(), editor.getLength()));
                    editor.showParagraphAtCenter(editor.getCurrentParagraph());
                    editor.requestFocus();
                    exportButton.setDisable(false);
                    updateNavigationButtons();
                });
            } catch (RuntimeException ex) {
                Platform.runLater(() -> { if (!disposed && request == navigationGeneration) setStatus("跳转失败：" + ex.getMessage()); });
            }
        });
    }

    private void navigateHistory(boolean back) {
        var from = back ? backLocations : forwardLocations;
        var to = back ? forwardLocations : backLocations;
        while (!from.isEmpty()) {
            NavigationLocation location = from.pop();
            if (!indexedNodes.containsKey(location.key())) continue;
            NavigationLocation current = currentLocation();
            if (current != null) to.push(current);
            openNavigationTarget(new SymbolNavigationService.Target(location.key(), location.start(), location.end(), ""), null, false);
            break;
        }
        updateNavigationButtons();
    }

    private void updateNavigationButtons() {
        navigationBackButton.setDisable(backLocations.isEmpty());
        navigationForwardButton.setDisable(forwardLocations.isEmpty());
    }

    private String decompiledSource(ClassNode node) {
        return decompiledSource(node, sourceCache);
    }

    private String decompiledSource(ClassNode node, Map<String, String> cache) {
        return cache.computeIfAbsent(classKey(node), key -> {
            try {
                byte[] bytes = node.getClassBytes();
                if (bytes == null) bytes = Files.readAllBytes(Path.of(node.getFullPath()));
                String className = new javassist.bytecode.ClassFile(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes))).getName().replace('.', '/');
                DecompileResult result = decompilerService.decompileFromBytes(bytes, className);
                if (!result.isSuccess()) throw new IllegalStateException(result.getErrorMessage());
                return result.getSourceCode();
            } catch (IOException ex) { throw new java.io.UncheckedIOException(ex); }
        });
    }

    private void decompileClass(ClassNode classNode) {
        setStatus(I18N.get("msg.decompiling", classNode.getName()));
        exportButton.setDisable(true);
        String editorKey = classKey(classNode);
        CodeArea existing = openEditors.get(editorKey);
        if (existing != null) {
            codeTabPane.getSelectionModel().select(openTabs.get(editorKey));
            exportButton.setDisable(existing.getText().isEmpty());
            return;
        }
        CodeArea editor = createEditor(classNode);
        codeArea = editor;
        Tab tab = openTabs.get(editorKey);
        if (tab != null) codeTabPane.getSelectionModel().select(tab);
        
        runAsync(() -> {
            try {
                String sourceCode = decompiledSource(classNode);
                var highlighting = syntaxHighlightService.computeHighlighting(sourceCode);
                
                Platform.runLater(() -> {
                    if (disposed || openEditors.get(editorKey) != editor) return;
                    if (!editor.getText().equals(sourceCode)) {
                        editor.clear();
                        editor.replaceText(0, 0, sourceCode);
                        
                        // 应用语法高亮
                        editor.setStyleSpans(0, highlighting);
                        
                        setStatus(I18N.get("msg.decompile_success"));
                        exportButton.setDisable(false);
                    }
                });
                
            } catch (Exception e) {
                logger.error("反编译失败", e);
                Platform.runLater(() -> {
                    if (disposed || openEditors.get(editorKey) != editor) return;
                    editor.clear();
                    editor.appendText(I18N.get("msg.decompile_error_comment") + e.getMessage());
                    setStatus(I18N.get("msg.decompile_failed"));
                });
            }
        });
    }

    private void onExport() {
        if (codeArea == null) return;
        String code = codeArea.getText();
        if (code.isEmpty()) {
            setStatus(I18N.get("msg.nothing_to_export"));
            return;
        }
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18N.get("dialog.export_java"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18N.get("filter.java_files"), "*.java"));
        String exportName = codeTabPane.getSelectionModel().getSelectedItem() == null ? "decompiled.java" :
                codeTabPane.getSelectionModel().getSelectedItem().getText().replace(".class", ".java");
        fileChooser.setInitialFileName(exportName);
        
        File file = fileChooser.showSaveDialog(rootPane.getScene().getWindow());
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(code);
                setStatus(I18N.get("msg.exported", file.getName()));
            } catch (IOException e) {
                logger.error("导出失败", e);
                setStatus(I18N.get("msg.export_failed", e.getMessage()));
            }
        }
    }

    private void onExpandJar(TreeItem<ClassNode> jarItem) {
        // 避免重复展开
        if (!jarItem.getChildren().isEmpty()) {
            jarItem.setExpanded(!jarItem.isExpanded());
            return;
        }
        
        ClassNode jarNode = jarItem.getValue();
        File jarFile = new File(jarNode.getFullPath());
        
        if (!jarFile.exists()) {
            setStatus(I18N.get("msg.jar_not_exists", jarFile.getName()));
            return;
        }
        
        setStatus(I18N.get("msg.expanding_jar", jarFile.getName()));
        
        runAsync(() -> {
            try {
                TreeItem<ClassNode> jarContent = jarLoaderService.loadJarFile(jarFile);
                
                Platform.runLater(() -> {
                    // 将jar内容添加到当前节点
                    jarItem.getChildren().setAll(jarContent.getChildren());
                    jarItem.setExpanded(true);
                    setStatus(I18N.get("msg.expanded_jar", jarFile.getName()));
                });
                
            } catch (IOException e) {
                logger.error("展开jar失败", e);
                Platform.runLater(() -> setStatus(I18N.get("msg.expand_failed", e.getMessage())));
            }
        });
    }

    private void onExportAll() {
        TreeItem<ClassNode> root = fileTreeView.getRoot();
        if (root == null) {
            setStatus(I18N.get("msg.load_jar_first"));
            return;
        }
        
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle(I18N.get("dialog.select_export_dir"));
        
        File exportDir = dirChooser.showDialog(rootPane.getScene().getWindow());
        if (exportDir == null) {
            return;
        }
        
        setStatus(I18N.get("msg.batch_exporting"));
        exportButton.setDisable(true);
        exportAllButton.setDisable(true);
        
        runAsync(() -> {
            try {
                int[] counts = {0, 0}; // [成功, 失败]
                exportTreeNode(root, exportDir, counts);
                
                int success = counts[0];
                int failed = counts[1];
                
                Platform.runLater(() -> {
                    setStatus(I18N.get("msg.batch_export_done", success, failed));
                    exportButton.setDisable(false);
                    exportAllButton.setDisable(false);
                });
                
            } catch (Exception e) {
                logger.error("批量导出失败", e);
                Platform.runLater(() -> {
                    setStatus(I18N.get("msg.batch_export_failed", e.getMessage()));
                    exportButton.setDisable(false);
                    exportAllButton.setDisable(false);
                });
            }
        });
    }
    
    private void exportTreeNode(TreeItem<ClassNode> node, File baseDir, int[] counts) {
        ClassNode classNode = node.getValue();
        
        // 如果是class文件，反编译并导出
        if (classNode.isClass()) {
            try {
                byte[] classBytes = classNode.getClassBytes();
                if (classBytes == null) {
                    // 从文件读取
                    classBytes = Files.readAllBytes(Paths.get(classNode.getFullPath()));
                }
                
                // 将路径转换为类名格式
                String className = classNode.getFullPath();
                if (className.endsWith(".class")) {
                    className = className.substring(0, className.length() - 6);
                }
                
                DecompileResult result = decompilerService.decompileFromBytes(classBytes, className);
                
                if (result.isSuccess()) {
                    // 构建输出文件路径
                    String relativePath = className.replace('/', File.separatorChar) + ".java";
                    File outputFile = new File(baseDir, relativePath);
                    
                    // 创建父目录
                    outputFile.getParentFile().mkdirs();
                    
                    // 写入文件
                    try (FileWriter writer = new FileWriter(outputFile)) {
                        writer.write(result.getSourceCode());
                    }
                    
                    counts[0]++; // 成功
                    
                    // 更新状态
                    int total = counts[0] + counts[1];
                    if (total % 10 == 0) {
                        Platform.runLater(() -> 
                            setStatus(I18N.get("msg.exporting_count", total))
                        );
                    }
                } else {
                    counts[1]++; // 失败
                    logger.warn("反编译失败: {}", className);
                }
                
            } catch (Exception e) {
                counts[1]++; // 失败
                logger.error("导出class失败: " + classNode.getName(), e);
            }
        }
        
        // 递归处理子节点
        for (TreeItem<ClassNode> child : node.getChildren()) {
            exportTreeNode(child, baseDir, counts);
        }
    }

    private void filterTree(String searchText) {
        if (fullTreeRoot == null) {
            return;
        }
        
        rebuildingFileTree = true;
        try {
        if (searchText == null || searchText.trim().isEmpty()) {
            // 恢复完整树
            fileTreeView.setRoot(fullTreeRoot);
            if (beforeTreeSearch != null) beforeTreeSearch.restore(fileTreeView);
            beforeTreeSearch = null;
            setStatus(I18N.get("msg.search_cleared"));
            return;
        }
        
        // 过滤树节点
        var currentState = TreeViewState.capture(fileTreeView, DecompilerController::treeNodeKey);
        if (beforeTreeSearch == null) beforeTreeSearch = currentState;
        TreeItem<ClassNode> filteredRoot = ClassTreeSearch.filter(fullTreeRoot, searchText);
        
        if (filteredRoot != null) {
            fileTreeView.setRoot(filteredRoot);
            currentState.restoreSelection(fileTreeView);
            setStatus(I18N.get("msg.search_found", searchText));
        } else {
            fileTreeView.setRoot(new TreeItem<>(fullTreeRoot.getValue()));
            setStatus(I18N.get("msg.search_no_match", searchText));
        }
        } finally { rebuildingFileTree = false; }
    }

    private static java.util.List<String> treeNodeKey(TreeItem<ClassNode> item) {
        ClassNode value = item.getValue();
        return value == null ? null : java.util.Arrays.asList(value.getSourcePath(), value.getFullPath(), value.getEntryPath());
    }
    private void expandAll(TreeItem<?> item) {
        if (item != null && !item.isLeaf()) {
            item.setExpanded(true);
            for (TreeItem<?> child : item.getChildren()) {
                expandAll(child);
            }
        }
    }

    private void setStatus(String message) {
        Platform.runLater(() -> {
            if (!disposed) statusLabel.setText(message);
        });
    }

    private void setFileInfo(File file) {
        long size = file.length();
        String sizeStr = size > 1024 * 1024 
            ? String.format("%.2f MB", size / (1024.0 * 1024.0))
            : String.format("%.2f KB", size / 1024.0);
        fileInfoLabel.setText(file.getName() + " (" + sizeStr + ")");
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        executor.shutdownNow();
        navigationExecutor.shutdownNow();
        editorNavigation.values().forEach(EditorNavigation::dispose);
        editorNavigation.clear(); navigationChoices.hide();
        sourceCache.clear(); navigationService = null;
        jarLoaderService.close();
    }
}
