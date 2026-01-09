package com.opencgl.decompiler.controller;

import com.opencgl.decompiler.i18n.I18N;
import com.opencgl.decompiler.model.ClassNode;
import com.opencgl.decompiler.model.DecompileResult;
import com.opencgl.decompiler.service.DecompilerService;
import com.opencgl.decompiler.service.JarLoaderService;
import com.opencgl.decompiler.service.SyntaxHighlightService;
import com.opencgl.decompiler.views.DecompilerView;
import javafx.application.Platform;
import javafx.fxml.Initializable;
import javafx.scene.control.TreeItem;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 反编译器控制器
 */
public class DecompilerController extends DecompilerView implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(DecompilerController.class);

    private DecompilerService decompilerService;
    private JarLoaderService jarLoaderService;
    private SyntaxHighlightService syntaxHighlightService;
    private TreeItem<ClassNode> fullTreeRoot; // 保存完整树用于搜索
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "opencgl-decompiler");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean disposed;

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
        // 初始化代码区域
        codeArea.setEditable(false);
        
        // 加载语法高亮CSS
        String css = getClass().getResource("/com/opencgl/decompiler/styles/java-highlighting.css").toExternalForm();
        codeArea.getStylesheets().add(css);
        
        // 初始化树视图
        fileTreeView.setShowRoot(true);
        
        // 初始禁用导出按钮
        exportButton.setDisable(true);
        exportAllButton.setDisable(true);
    }

    private void bindEvents() {
        openFileButton.setOnAction(e -> onOpenFile());
        openFolderButton.setOnAction(e -> onOpenFolder());
        exportButton.setOnAction(e -> onExport());
        exportAllButton.setOnAction(e -> onExportAll());
        
        // 树节点选择事件
        fileTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getValue().isClass()) {
                decompileClass(newVal.getValue());
            }
        });
        
        // 双击jar文件展开
        fileTreeView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TreeItem<ClassNode> item = fileTreeView.getSelectionModel().getSelectedItem();
                if (item != null && item.getValue().getName().endsWith(".jar")) {
                    onExpandJar(item);
                }
            }
        });
        
        // 搜索功能
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterTree(newVal);
        });
    }

    private void onOpenFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18N.get("dialog.select_file"));
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter(I18N.get("filter.java_files"), "*.jar", "*.class", "*.zip"),
            new FileChooser.ExtensionFilter(I18N.get("filter.all_files"), "*.*")
        );
        
        File file = fileChooser.showOpenDialog(rootPane.getScene().getWindow());
        if (file != null) {
            loadFile(file);
        }
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
        
        runAsync(() -> {
            try {
                TreeItem<ClassNode> root;
                if (file.getName().endsWith(".class")) {
                    root = jarLoaderService.loadClassFile(file);
                } else {
                    root = jarLoaderService.loadJarFile(file);
                }
                
                Platform.runLater(() -> {
                    fileTreeView.setRoot(root);
                    fullTreeRoot = root; // 保存完整树
                    setStatus(I18N.get("msg.loaded_file", file.getName()));
                    setFileInfo(file);
                    exportAllButton.setDisable(false);
                });
                
            } catch (IOException e) {
                logger.error("加载文件失败", e);
                Platform.runLater(() -> setStatus(I18N.get("msg.load_failed", e.getMessage())));
            }
        });
    }

    private void loadDirectory(File dir) {
        setStatus(I18N.get("msg.scanning_dir", dir.getName()));
        
        runAsync(() -> {
            try {
                ClassNode rootNode = new ClassNode(dir.getName(), dir.getAbsolutePath(), false, true);
                TreeItem<ClassNode> root = new TreeItem<>(rootNode);
                root.setExpanded(true);
                
                // 递归扫描文件夹
                scanDirectory(dir.toPath(), root, "");
                
                Platform.runLater(() -> {
                    fileTreeView.setRoot(root);
                    setStatus(I18N.get("msg.scanned_dir", dir.getName()));
                    setFileInfo(dir);
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
                if (fileName.endsWith(".class") || fileName.endsWith(".jar")) {
                    String relativePath = dir.relativize(file).toString();
                    ClassNode node = new ClassNode(fileName, file.toString(), fileName.endsWith(".class"), false);
                    Platform.runLater(() -> parent.getChildren().add(new TreeItem<>(node)));
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void decompileClass(ClassNode classNode) {
        setStatus(I18N.get("msg.decompiling", classNode.getName()));
        exportButton.setDisable(true);
        
        runAsync(() -> {
            try {
                byte[] classBytes = classNode.getClassBytes();
                if (classBytes == null) {
                    // 从文件读取
                    classBytes = Files.readAllBytes(Paths.get(classNode.getFullPath()));
                }
                
                // 将路径转换为类名格式 (com/opencgl/Example.class -> com/opencgl/Example)
                String className = classNode.getFullPath();
                if (className.endsWith(".class")) {
                    className = className.substring(0, className.length() - 6);
                }
                
                DecompileResult result = decompilerService.decompileFromBytes(classBytes, className);
                
                Platform.runLater(() -> {
                    if (result.isSuccess()) {
                        String sourceCode = result.getSourceCode();
                        codeArea.clear();
                        codeArea.replaceText(0, 0, sourceCode);
                        
                        // 应用语法高亮
                        codeArea.setStyleSpans(0, syntaxHighlightService.computeHighlighting(sourceCode));
                        
                        classNameLabel.setText("☕ " + classNode.getName());
                        long lineCount = sourceCode.lines().count();
                        lineCountLabel.setText(lineCount + I18N.get("label.lines"));
                        
                        setStatus(I18N.get("msg.decompile_success"));
                        exportButton.setDisable(false);
                    } else {
                        codeArea.clear();
                        codeArea.appendText(I18N.get("msg.decompile_failed_comment") + result.getErrorMessage());
                        setStatus(I18N.get("msg.decompile_failed"));
                    }
                });
                
            } catch (Exception e) {
                logger.error("反编译失败", e);
                Platform.runLater(() -> {
                    codeArea.clear();
                    codeArea.appendText(I18N.get("msg.decompile_error_comment") + e.getMessage());
                    setStatus(I18N.get("msg.decompile_failed"));
                });
            }
        });
    }

    private void onExport() {
        String code = codeArea.getText();
        if (code.isEmpty()) {
            setStatus(I18N.get("msg.nothing_to_export"));
            return;
        }
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18N.get("dialog.export_java"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18N.get("filter.java_files"), "*.java"));
        fileChooser.setInitialFileName(classNameLabel.getText().replace("☕ ", "").replace(".class", ".java"));
        
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
        
        if (searchText == null || searchText.trim().isEmpty()) {
            // 恢复完整树
            fileTreeView.setRoot(fullTreeRoot);
            setStatus(I18N.get("msg.search_cleared"));
            return;
        }
        
        // 过滤树节点
        String lowerSearch = searchText.toLowerCase();
        TreeItem<ClassNode> filteredRoot = filterNode(fullTreeRoot, lowerSearch);
        
        if (filteredRoot != null) {
            fileTreeView.setRoot(filteredRoot);
            expandAll(filteredRoot);
            setStatus(I18N.get("msg.search_found", searchText));
        } else {
            fileTreeView.setRoot(fullTreeRoot);
            setStatus(I18N.get("msg.search_no_match", searchText));
        }
    }
    
    private TreeItem<ClassNode> filterNode(TreeItem<ClassNode> node, String searchText) {
        ClassNode value = node.getValue();
        boolean matches = value.getName().toLowerCase().contains(searchText);
        
        TreeItem<ClassNode> copy = new TreeItem<>(value);
        boolean hasChildren = false;
        
        for (TreeItem<ClassNode> child : node.getChildren()) {
            TreeItem<ClassNode> filteredChild = filterNode(child, searchText);
            if (filteredChild != null) {
                copy.getChildren().add(filteredChild);
                hasChildren = true;
            }
        }
        
        if (matches || hasChildren) {
            return copy;
        }
        
        return null;
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
    }
}
