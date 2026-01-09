package com.opencgl.plugin.ragingestion.controller;

import com.opencgl.plugin.ragingestion.engine.ChunkerEngine;
import com.opencgl.plugin.ragingestion.engine.EmbeddingEngine;
import com.opencgl.plugin.ragingestion.engine.HashUtil;
import com.opencgl.plugin.ragingestion.engine.IncrementalDiffEngine;
import com.opencgl.plugin.ragingestion.engine.LocalOfflineEmbeddingEngine;
import com.opencgl.plugin.ragingestion.engine.RemoteApiEmbeddingEngine;
import com.opencgl.plugin.ragingestion.export.SearchCodeGenerator;
import com.opencgl.plugin.ragingestion.gateway.EmbeddedRestGateway;
import com.opencgl.plugin.ragingestion.model.DocumentChunk;
import com.opencgl.plugin.ragingestion.model.SearchResult;
import com.opencgl.plugin.ragingestion.storage.ElasticsearchVectorDriver;
import com.opencgl.plugin.ragingestion.storage.PgVectorDriver;
import com.opencgl.plugin.ragingestion.storage.SqliteVectorDriver;
import com.opencgl.plugin.ragingestion.storage.VectorStorageDriver;

import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import com.opencgl.plugin.ragingestion.workspace.KbProfileManager;
import com.opencgl.plugin.ragingestion.workspace.KbWorkspaceProfile;
import com.opencgl.plugin.ragingestion.storage.FaissLocalVectorDriver;
import com.opencgl.plugin.ragingestion.storage.MilvusVectorDriver;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RagIngestionWidgetController {
    private final Set<Task<?>> runningTasks = ConcurrentHashMap.newKeySet();
    private volatile boolean disposed;

    @FXML private ComboBox<KbWorkspaceProfile> kbProfileCombo;
    @FXML private Button saveProfileBtn;
    @FXML private Button deleteProfileBtn;
    @FXML private Button browseDbPathBtn;

    // 历史在库知识维护组件
    @FXML private TextField fileFilterField;
    @FXML private CheckBox autoStrategyCheck;

    @FXML private TextField dbSearchField;
    @FXML private TableView<DocumentChunk> dbExplorerTableView;
    @FXML private TableColumn<DocumentChunk, Integer> colDbIndex;
    @FXML private TableColumn<DocumentChunk, String> colDbId;
    @FXML private TableColumn<DocumentChunk, String> colDbSource;
    @FXML private TableColumn<DocumentChunk, String> colDbQuestion;
    @FXML private TableColumn<DocumentChunk, String> colDbAnswer;
    @FXML private Label dbStatusLabel;
    @FXML private Button refreshDbBtn;
    @FXML private Button dbEditRowBtn;
    @FXML private Button deleteDbRowBtn;

    @FXML private TextField filePathField;
    @FXML private Button browseFileBtn;
    @FXML private CheckBox foldBlankCheck;
    @FXML private CheckBox trimSpaceCheck;
    @FXML private TextField excludeRegexField;
    @FXML private ComboBox<String> strategyCombo;
    @FXML private TextField customDelimiterField;
    @FXML private TextField chunkSizeField;
    @FXML private TextField overlapField;
    @FXML private CheckBox enableIncrementalCheck;

    @FXML private RadioButton modeApiRadio;
    @FXML private RadioButton modeLocalRadio;
    @FXML private TextField modelNameField;
    @FXML private TextField dimensionField;

    @FXML private TextField apiUrlField;
    @FXML private PasswordField apiKeyField;

    @FXML private ComboBox<String> storageTypeCombo;
    @FXML private TextField dbConnStringField;
    @FXML private TextField targetNameField;

    @FXML private Button previewChunksBtn;
    @FXML private Button startIngestBtn;
    @FXML private Button toggleRestBtn;

    @FXML private TableView<DocumentChunk> chunkTableView;
    @FXML private TableColumn<DocumentChunk, Integer> colIndex;
    @FXML private TableColumn<DocumentChunk, String> colRuleMatched;
    @FXML private TableColumn<DocumentChunk, String> colDiffStatus;
    @FXML private TableColumn<DocumentChunk, String> colProcessStatus;
    @FXML private TableColumn<DocumentChunk, Integer> colLength;
    @FXML private TableColumn<DocumentChunk, String> colChunkId;
    @FXML private TableColumn<DocumentChunk, String> colQuestion;
    @FXML private TableColumn<DocumentChunk, String> colAnswer;

    @FXML private Button browseDirBtn;
    @FXML private Button localModelBrowseBtn;
    @FXML private HBox apiUrlBox;
    @FXML private Button browseDbDirBtn;
    @FXML private Button openEditDialogBtn;
    @FXML private Label tableSubtitleLabel;
    @FXML private Button deleteSelectedBtn;
    @FXML private Button addNewQaBtn;

    @FXML private Label chunkCountLabel;
    @FXML private Label statusBadge;

    @FXML private ProgressBar progressBar;
    @FXML private Label progressStatusLabel;
    @FXML private TextArea consoleArea;

    // Search Tab
    @FXML private TextField searchQueryField;
    @FXML private TextField topKField;
    @FXML private TextField minScoreField;
    @FXML private Button executeSearchBtn;

    @FXML private TableView<SearchResult> searchTableView;
    @FXML private TableColumn<SearchResult, Integer> colRank;
    @FXML private TableColumn<SearchResult, String> colScore;
    @FXML private TableColumn<SearchResult, String> colSearchFile;
    @FXML private TableColumn<SearchResult, String> colSearchContent;

    @FXML private ComboBox<String> exportLangCombo;
    @FXML private Button exportCodeBtn;
    @FXML private TextArea codeExportArea;

    private final ObservableList<DocumentChunk> chunkList = FXCollections.observableArrayList();
    private final ObservableList<SearchResult> searchList = FXCollections.observableArrayList();
    private final ObservableList<DocumentChunk> dbExplorerList = FXCollections.observableArrayList();
    private final EmbeddedRestGateway restGateway = new EmbeddedRestGateway();
    private final KbProfileManager profileManager = new KbProfileManager();
    private ToggleGroup modeGroup;

    @FXML
    public void initialize() {
        // Init ToggleGroup
        modeGroup = new ToggleGroup();
        modeApiRadio.setToggleGroup(modeGroup);
        modeLocalRadio.setToggleGroup(modeGroup);

        if (apiUrlBox != null) {
            apiUrlBox.managedProperty().bind(apiUrlBox.visibleProperty());
            apiUrlBox.visibleProperty().bind(modeApiRadio.selectedProperty());
        }
        if (localModelBrowseBtn != null) {
            localModelBrowseBtn.managedProperty().bind(localModelBrowseBtn.visibleProperty());
            localModelBrowseBtn.visibleProperty().bind(modeLocalRadio.selectedProperty());
        }
        modeGroup.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            if (modeLocalRadio.isSelected()) {
                if (modelNameField != null) {
                    modelNameField.setPromptText("选择或输入离线模型权重路径 (.onnx / 内置)");
                    if ("text-embedding-3-small".equals(modelNameField.getText())) {
                        modelNameField.setText("bge-small-zh-v1.5");
                        if (dimensionField != null) dimensionField.setText("512");
                    }
                }
            } else {
                if (modelNameField != null) {
                    modelNameField.setPromptText("远程模型名 (如 text-embedding-3-small)");
                    if ("bge-small-zh-v1.5".equals(modelNameField.getText())) {
                        modelNameField.setText("text-embedding-3-small");
                        if (dimensionField != null) dimensionField.setText("768");
                    }
                }
            }
        });

        // Init KB Workspace Profile Combo
        if (kbProfileCombo != null) {
            kbProfileCombo.setItems(FXCollections.observableArrayList(profileManager.getProfiles()));
            if (!kbProfileCombo.getItems().isEmpty()) {
                kbProfileCombo.getSelectionModel().select(0);
                applyWorkspaceProfile(kbProfileCombo.getItems().get(0));
            }
            kbProfileCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    applyWorkspaceProfile(newVal);
                }
            });
        }

        // Init ComboBoxes
        List<String> strategyLabels = new ArrayList<>();
        for (ChunkerEngine.Strategy s : ChunkerEngine.Strategy.values()) {
            strategyLabels.add(s.getLabel());
        }
        strategyCombo.setItems(FXCollections.observableArrayList(strategyLabels));
        strategyCombo.getSelectionModel().select(0);
        strategyCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            boolean isQa = newV != null && newV.contains("FAQ");
            updateAdaptiveLayout(isQa);
        });
        updateAdaptiveLayout(false);

        storageTypeCombo.setItems(FXCollections.observableArrayList(
                "SQLITE", "FAISS_LOCAL", "MILVUS", "PGVECTOR", "ELASTICSEARCH"
        ));
        storageTypeCombo.getSelectionModel().select(0);

        // 动态监听数据库类型切换路径输入框与默认值
        storageTypeCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && dbConnStringField != null) {
                if ("FAISS_LOCAL".equalsIgnoreCase(newVal)) {
                    dbConnStringField.setPromptText("本地 FAISS 索引快照路径 (*.index)");
                    if (dbConnStringField.getText().isEmpty() || dbConnStringField.getText().contains(":")) {
                        dbConnStringField.setText("rag_faiss.index");
                    }
                } else if ("MILVUS".equalsIgnoreCase(newVal)) {
                    dbConnStringField.setPromptText("远程 Milvus 主机 IP:端口 (如 localhost:19530)");
                    dbConnStringField.setText("localhost:19530");
                } else if ("SQLITE".equalsIgnoreCase(newVal)) {
                    dbConnStringField.setPromptText("本地 SQLite 文件 (.db)");
                    if (dbConnStringField.getText().isEmpty() || dbConnStringField.getText().contains(":")) {
                        dbConnStringField.setText("rag_kb.db");
                    }
                }
            }
        });

        exportLangCombo.setItems(FXCollections.observableArrayList(
                "PYTHON", "JAVA", "CURL"
        ));
        exportLangCombo.getSelectionModel().select(0);

        // Bind Table Columns
        colIndex.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getChunkIndex() + 1).asObject());
        if (colRuleMatched != null) {
            colRuleMatched.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getRuleMatched()));
        }
        colDiffStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDiffStatus()));
        colProcessStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProcessStatus()));
        colLength.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getCharLength()).asObject());
        colChunkId.setCellValueFactory(data -> new SimpleStringProperty(truncate(data.getValue().getChunkId(), 14)));
        if (colQuestion != null) {
            colQuestion.setCellValueFactory(data -> {
                DocumentChunk c = data.getValue();
                String q = c.isQaPair() ? c.getQaQuestion() : truncate(c.getContent(), 60);
                return new SimpleStringProperty(q != null ? q : "");
            });
        }
        if (colAnswer != null) {
            colAnswer.setCellValueFactory(data -> {
                DocumentChunk c = data.getValue();
                String a = c.isQaPair() ? c.getQaAnswer() : c.getContent();
                return new SimpleStringProperty(a != null ? a : "");
            });
        }
        chunkTableView.setItems(chunkList);

        chunkTableView.setRowFactory(tv -> {
            TableRow<DocumentChunk> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    openChunkEditModalDialog(row.getItem());
                }
            });
            return row;
        });
        chunkTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                updateAdaptiveLayout(newVal.isQaPair());
            }
        });

        colRank.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getRank()).asObject());
        colScore.setCellValueFactory(data -> new SimpleStringProperty(String.format("%.4f", data.getValue().getScore())));
        colSearchFile.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFileName()));
        colSearchContent.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getContent()));
        searchTableView.setItems(searchList);

        // 绑定在库切片看板 TableView (DB Explorer)
        if (dbExplorerTableView != null) {
            colDbIndex.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getChunkIndex() + 1).asObject());
            colDbId.setCellValueFactory(data -> new SimpleStringProperty(truncate(data.getValue().getChunkId(), 14)));
            colDbSource.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFileName()));
            colDbQuestion.setCellValueFactory(data -> {
                DocumentChunk c = data.getValue();
                String q = c.getQaQuestion();
                if (q == null || q.isEmpty()) q = truncate(c.getContent(), 50);
                return new SimpleStringProperty(q);
            });
            colDbAnswer.setCellValueFactory(data -> {
                DocumentChunk c = data.getValue();
                String a = c.getQaAnswer();
                if (a == null || a.isEmpty()) a = c.getContent();
                return new SimpleStringProperty(a);
            });
            dbExplorerTableView.setItems(dbExplorerList);
            dbExplorerTableView.setRowFactory(tv -> {
                TableRow<DocumentChunk> row = new TableRow<>();
                row.setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && (!row.isEmpty())) {
                        onOpenDbEditDialog();
                    }
                });
                return row;
            });
        }

        logInfo("RagIngestionToolModule Dify 交互级分段与 FAQ 人工调优控制台初始化就绪。");
        onGenerateExportCode();
    }

    private void updateAdaptiveLayout(boolean isQaMode) {
        if (colQuestion != null) colQuestion.setVisible(isQaMode);
        if (colAnswer != null) {
            colAnswer.setText(isQaMode ? "解答 A / 完整内容 (召回原始载荷)" : "切片完整正文段落 (计算 Embedding 向量标的 & 召回全文)");
            colAnswer.setPrefWidth(isQaMode ? 360 : 600);
        }
        if (tableSubtitleLabel != null) {
            tableSubtitleLabel.setText(isQaMode
                    ? "分段清单预览 (FAQ双列模式：双击任意行打开弹窗精修 Q/A)"
                    : "分段清单预览 (单列正文模式：双击任意行打开弹窗精修切片正文)");
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    @FXML
    public void onBrowseFile(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择待导入切片的文档");
        File file = fileChooser.showOpenDialog(filePathField.getScene().getWindow());
        if (file != null) {
            filePathField.setText(file.getAbsolutePath());
            logInfo("选中待解析文件: " + file.getAbsolutePath());
        }
    }

    @FXML
    public void onBrowseDirectory(ActionEvent event) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("选择待批量导入切片的文档目录");
        File dir = dc.showDialog(filePathField.getScene().getWindow());
        if (dir != null) {
            filePathField.setText(dir.getAbsolutePath());
            logInfo("选中待解析文件夹: " + dir.getAbsolutePath());
        }
    }

    @FXML
    public void onBrowseDbDir(ActionEvent event) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("选择本地向量数据库存放目录");
        File dir = dc.showDialog(dbConnStringField.getScene().getWindow());
        if (dir != null) {
            String storageType = storageTypeCombo != null ? storageTypeCombo.getValue() : "SQLITE";
            String defaultFileName = "FAISS_LOCAL".equalsIgnoreCase(storageType) ? "rag_faiss.index" : "rag_kb.db";
            File targetFile = new File(dir, defaultFileName);
            dbConnStringField.setText(targetFile.getAbsolutePath());
            logInfo("选中本地库路径: " + targetFile.getAbsolutePath());
        }
    }

    @FXML
    public void onBrowseLocalModel(ActionEvent event) {
        FileChooser fc = new FileChooser();
        fc.setTitle("选择离线 Embedding 模型文件 (*.onnx / *.bin)");
        File modelFile = fc.showOpenDialog(modelNameField.getScene().getWindow());
        if (modelFile != null) {
            modelNameField.setText(modelFile.getAbsolutePath());
            if (dimensionField != null) dimensionField.setText("512");
            logInfo("选中离线模型权重: " + modelFile.getAbsolutePath());
        }
    }

    private List<File> collectTextFiles(File dirOrFile) {
        List<File> list = new ArrayList<>();
        if (dirOrFile == null || !dirOrFile.exists()) return list;
        if (dirOrFile.isFile()) {
            list.add(dirOrFile);
            return list;
        }
        File[] children = dirOrFile.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    String name = child.getName();
                    if (name.startsWith(".") || "target".equals(name) || "node_modules".equals(name) || "build".equals(name)) continue;
                    list.addAll(collectTextFiles(child));
                } else {
                    if (matchesFileFilter(child.getName(), fileFilterField != null ? fileFilterField.getText() : "")) {
                        list.add(child);
                    }
                }
            }
        }
        return list;
    }

    private boolean matchesFileFilter(String fileName, String filterStr) {
        String lower = fileName.toLowerCase();
        if (filterStr == null || filterStr.trim().isEmpty()) {
            return lower.endsWith(".md") || lower.endsWith(".txt") || lower.endsWith(".faq") || lower.endsWith(".csv")
                    || lower.endsWith(".java") || lower.endsWith(".py") || lower.endsWith(".js") || lower.endsWith(".json")
                    || lower.endsWith(".sql") || lower.endsWith(".xml") || lower.endsWith(".html");
        }
        String[] tokens = filterStr.split("[,;\\s]+");
        for (String t : tokens) {
            String token = t.trim().toLowerCase();
            if (token.startsWith("*.")) {
                if (lower.endsWith(token.substring(1))) return true;
            } else if (token.startsWith(".")) {
                if (lower.endsWith(token)) return true;
            } else if (!token.isEmpty()) {
                if (lower.contains(token)) return true;
            }
        }
        return false;
    }

    @FXML
    public void onPreviewChunks(ActionEvent event) {
        String path = filePathField.getText();
        if (path == null || path.trim().isEmpty()) {
            showAlert("提示", "请先填写或选择要切片的文档或目录路径！");
            return;
        }
        File file = new File(path.trim());
        if (!file.exists()) {
            showAlert("错误", "指定目标不存在: " + path);
            return;
        }

        int size = parseInteger(chunkSizeField.getText(), 500);
        int overlap = parseInteger(overlapField.getText(), 50);
        ChunkerEngine.Strategy strategy = resolveStrategy(strategyCombo.getValue());
        String customDelim = customDelimiterField != null ? customDelimiterField.getText() : "";
        ChunkerEngine.PreProcessConfig preProc = new ChunkerEngine.PreProcessConfig(
                foldBlankCheck != null && foldBlankCheck.isSelected(),
                trimSpaceCheck != null && trimSpaceCheck.isSelected(),
                excludeRegexField != null ? excludeRegexField.getText() : ""
        );

        Task<Void> previewTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateProgress(0.1, 1.0);
                updateMessage("正在对文件源执行语义解析与切分...");
                List<File> targetFiles = collectTextFiles(file);
                List<DocumentChunk> chunks = new ArrayList<>();
                int globalIdx = 0;
                for (File tf : targetFiles) {
                    ChunkerEngine.Strategy fileStrategy = strategy;
                    if (autoStrategyCheck != null && autoStrategyCheck.isSelected()) {
                        String lowerName = tf.getName().toLowerCase();
                        if (lowerName.endsWith(".faq")) {
                            fileStrategy = ChunkerEngine.Strategy.QA_FAQ_PAIR;
                        } else if (lowerName.endsWith(".md")) {
                            fileStrategy = ChunkerEngine.Strategy.HEADER_HIERARCHY;
                        }
                    }
                    List<DocumentChunk> sub = ChunkerEngine.splitFile(tf, fileStrategy, size, overlap, customDelim, preProc);
                    for (DocumentChunk c : sub) {
                        c.setChunkIndex(globalIdx++);
                        chunks.add(c);
                    }
                }
                
                updateProgress(0.5, 1.0);
                if (enableIncrementalCheck.isSelected()) {
                    updateMessage("开启 SHA-256 增量对账：正在核对目的库现存 Chunk...");
                    VectorStorageDriver driver = buildStorageDriver();
                    int dim = parseInteger(dimensionField.getText(), 768);
                    driver.ensureTableOrIndexExists(targetNameField.getText().trim(), dim);
                    IncrementalDiffEngine.compare(chunks, file.getAbsolutePath(), targetNameField.getText().trim(), driver);
                }

                runOnUi(() -> {
                    chunkList.setAll(chunks);
                    if (chunkCountLabel != null) {
                        chunkCountLabel.setText("共 " + chunks.size() + " 块");
                    }
                    long unchangedCount = chunks.stream().filter(c -> "UNCHANGED".equals(c.getDiffStatus())).count();
                    long newCount = chunks.size() - unchangedCount;
                    logInfo(String.format("切片预览就绪：共 %d 块 | %d 块新/变动(待算) | %d 块指纹吻合(已存库跳过)",
                            chunks.size(), newCount, unchangedCount));

                    boolean requestedQa = (strategy == ChunkerEngine.Strategy.QA_FAQ_PAIR) ||
                            (autoStrategyCheck != null && autoStrategyCheck.isSelected() && file.getName().toLowerCase().endsWith(".faq"));
                    boolean hasAnyQaPair = chunks.stream().anyMatch(DocumentChunk::isQaPair);
                    if (requestedQa && !hasAnyQaPair && !chunks.isEmpty()) {
                        showAlert("问答对匹配格式提醒",
                                "当前选用了【FAQ 问答对 (QA_FAQ_PAIR)】分块策略，但系统未能从文档中识别出标准问答匹配标记！\n\n" +
                                "为防止内容丢失，系统已自动将其作为【普通正文切片】兜底载入表格。\n\n" +
                                "💡 格式建议：提问行建议以 Q: / 问: / 问题: 开头，解答行建议以 A: / 答: 开头；亦可切换为常规递归语义切片。");
                    }
                });
                updateProgress(1.0, 1.0);
                updateMessage("切片预览及对齐完毕");
                return null;
            }
        };

        progressBar.progressProperty().bind(previewTask.progressProperty());
        progressStatusLabel.textProperty().bind(previewTask.messageProperty());
        startTask(previewTask, "rag-preview");
    }

    @FXML
    public void onStartIngest(ActionEvent event) {
        if (chunkList.isEmpty()) {
            showAlert("提示", "切片清单为空，请先执行【1. 预览切片与增量比对】！");
            return;
        }

        Task<Void> ingestTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                EmbeddingEngine embeddingEngine = buildEmbeddingEngine();
                VectorStorageDriver storageDriver = buildStorageDriver();
                String targetName = targetNameField.getText().trim();
                int dim = parseInteger(dimensionField.getText(), 768);

                updateMessage("校验目标表结构...");
                storageDriver.ensureTableOrIndexExists(targetName, dim);

                List<DocumentChunk> pendingChunks = new ArrayList<>();
                for (DocumentChunk chunk : chunkList) {
                    if ("UNCHANGED".equals(chunk.getDiffStatus()) && enableIncrementalCheck.isSelected()) {
                        chunk.setProcessStatus("SKIPPED");
                    } else {
                        pendingChunks.add(chunk);
                    }
                }

                if (pendingChunks.isEmpty()) {
                    runOnUi(() -> {
                        chunkTableView.refresh();
                        logInfo("增量判定结果：所有分块内容及指纹未发生改变，已全部跳过入库操作！");
                    });
                    updateProgress(1.0, 1.0);
                    updateMessage("无变更需要处理");
                    return null;
                }

                int total = pendingChunks.size();
                logInfo(String.format("开始计算与入库：待计算切片 %d 块，引擎=[%s]，存储=[%s]",
                        total, embeddingEngine.getEngineName(), storageDriver.getDriverName()));

                int batchSize = 16;
                int processed = 0;
                for (int i = 0; i < total; i += batchSize) {
                    int end = Math.min(total, i + batchSize);
                    List<DocumentChunk> batch = pendingChunks.subList(i, end);
                    List<String> texts = new ArrayList<>();
                    for (DocumentChunk c : batch) {
                        c.setProcessStatus("EMBEDDING");
                        texts.add(c.getEmbedTargetText());
                    }
                    runOnUi(() -> chunkTableView.refresh());

                    List<float[]> vectors = embeddingEngine.embedBatch(texts);
                    for (int j = 0; j < batch.size(); j++) {
                        batch.get(j).setEmbedding(vectors.get(j));
                        batch.get(j).setProcessStatus("SUCCESS");
                    }

                    storageDriver.upsertChunks(targetName, batch);
                    processed += batch.size();
                    updateProgress((double) processed / total, 1.0);
                    updateMessage(String.format("已入表 %d / %d 块", processed, total));
                    runOnUi(() -> chunkTableView.refresh());
                }

                logInfo("成功完成批量切片向量计算与写入入库流程！");
                updateProgress(1.0, 1.0);
                updateMessage("入库完成");
                return null;
            }
        };

        progressBar.progressProperty().bind(ingestTask.progressProperty());
        progressStatusLabel.textProperty().bind(ingestTask.messageProperty());
        startTask(ingestTask, "rag-ingest");
    }

    @FXML
    public void onOpenEditDialog(ActionEvent event) {
        DocumentChunk sel = chunkTableView.getSelectionModel().getSelectedItem();
        if (sel == null) {
            showAlert("提示", "请先在上方表格选中需要精微调整或校对的切片记录！");
            return;
        }
        openChunkEditModalDialog(sel);
    }

    private void openChunkEditModalDialog(DocumentChunk sel) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("段落切片内容人工精修与校对 - 序号 #" + (sel.getChunkIndex() + 1));
        dialog.setHeaderText("可直接修改提问/正文；确定保存后系统自动重算 SHA-256 增量指纹并标为待入库：");
        dialog.setResizable(true);

        ButtonType saveBtnType = new ButtonType("✔ 确认修改并更新指纹", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        VBox contentBox = new VBox(10);
        contentBox.setPrefSize(640, 420);
        contentBox.setStyle("-fx-padding: 14;");

        TextArea qArea = new TextArea();
        TextArea aArea = new TextArea();
        qArea.setWrapText(true);
        aArea.setWrapText(true);

        if (sel.isQaPair()) {
            qArea.setText(sel.getQaQuestion() != null ? sel.getQaQuestion() : "");
            aArea.setText(sel.getQaAnswer() != null ? sel.getQaAnswer() : "");
            qArea.setPrefHeight(110);
            aArea.setPrefHeight(240);
            contentBox.getChildren().addAll(
                    new Label("提问 Q (参与 Embedding 向量计算):"),
                    qArea,
                    new Label("完整正文 / 解答 A (召回后返回的全量正文载荷):"),
                    aArea
            );
        } else {
            aArea.setText(sel.getContent() != null ? sel.getContent() : "");
            aArea.setPrefHeight(340);
            contentBox.getChildren().addAll(
                    new Label("切片完整正文段落 (既作为 Embedding 计算标的，又作为完整召回载荷):"),
                    aArea
            );
        }

        Label hashPreviewLabel = new Label("当前 SHA-256 指纹: " + sel.getChunkId());
        hashPreviewLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");
        contentBox.getChildren().add(hashPreviewLabel);

        dialog.getDialogPane().setContent(contentBox);
        dialog.setResultConverter(btn -> btn == saveBtnType);

        dialog.showAndWait().ifPresent(saved -> {
            if (saved) {
                if (sel.isQaPair()) {
                    sel.setQaQuestion(qArea.getText().trim());
                    sel.setQaAnswer(aArea.getText().trim());
                } else {
                    sel.setContent(aArea.getText().trim());
                }
                String recalculatedHash = HashUtil.sha256(sel.getContent());
                sel.setChunkId(recalculatedHash);
                sel.setDiffStatus("MODIFIED");
                sel.setProcessStatus("PENDING");
                if (dbExplorerList.contains(sel)) {
                    Task<Void> updateDbTask = new Task<>() {
                        @Override
                        protected Void call() throws Exception {
                            EmbeddingEngine embEngine = buildEmbeddingEngine();
                            VectorStorageDriver driver = buildStorageDriver();
                            String targetText = sel.isQaPair() && sel.getQaQuestion() != null && !sel.getQaQuestion().isEmpty()
                                    ? sel.getQaQuestion() : sel.getContent();
                            List<float[]> vecs = embEngine.embedBatch(List.of(targetText));
                            sel.setEmbedding(vecs.get(0));
                            driver.updateChunk(targetNameField.getText().trim(), sel);
                            return null;
                        }
                    };
                    updateDbTask.setOnSucceeded(e -> {
                        if (dbExplorerTableView != null) dbExplorerTableView.refresh();
                        logInfo("已存库记录重算向量同步更新完成，新 SHA-256 为: " + recalculatedHash);
                    });
                    startTask(updateDbTask, "rag-update-db");
                } else {
                    if (chunkTableView != null) chunkTableView.refresh();
                }
                logInfo(String.format("切片 #%d 已手动精修并完成校对，SHA-256 指纹更新为: %s",
                        sel.getChunkIndex() + 1, recalculatedHash));
            }
        });
    }

    @FXML
    public void onDeleteSelected(ActionEvent event) {
        DocumentChunk sel = chunkTableView.getSelectionModel().getSelectedItem();
        if (sel == null) {
            showAlert("提示", "请先选中需要移出入表队列的问答或段落记录！");
            return;
        }
        chunkList.remove(sel);
        if (chunkCountLabel != null) {
            chunkCountLabel.setText("共 " + chunkList.size() + " 块");
        }
        logInfo("已把选中项移出入库队列。");
    }

    @FXML
    public void onAddNewQa(ActionEvent event) {
        int nextIdx = chunkList.size();
        String contentText = "Q: 新增提问\nA: 新增解答";
        DocumentChunk newChunk = new DocumentChunk(
                "manual-" + System.currentTimeMillis(),
                "manual_qa.faq",
                "手工插入Q&A",
                nextIdx,
                contentText,
                HashUtil.sha256(contentText)
        );
        newChunk.setQaQuestion("新增提问：请在此输入提问 Q (送算 Embedding 向量化)");
        newChunk.setQaAnswer("新增解答：请在此输入解答 A / 全文正文 (命中召回完整载荷)");
        newChunk.setRuleMatched("手工插入Q&A");
        newChunk.setDiffStatus("NEW");
        newChunk.setProcessStatus("PENDING");
        chunkList.add(newChunk);
        chunkTableView.getSelectionModel().select(newChunk);
        if (chunkCountLabel != null) {
            chunkCountLabel.setText("共 " + chunkList.size() + " 块");
        }
        logInfo("已手工追加新问答对 [# " + (nextIdx + 1) + "]，可立即在下方编辑并保存。");
    }

    @FXML
    public void onExecuteSearch(ActionEvent event) {
        String query = searchQueryField.getText();
        if (query == null || query.trim().isEmpty()) {
            showAlert("提示", "请输入搜索关键问句或文本！");
            return;
        }
        int topK = parseInteger(topKField.getText(), 5);
        double minScore = parseDouble(minScoreField.getText(), 0.65);

        Task<Void> searchTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("正在计算问题语句的向量化表征...");
                EmbeddingEngine embEngine = buildEmbeddingEngine();
                VectorStorageDriver storageDriver = buildStorageDriver();

                List<float[]> queryVecs = embEngine.embedBatch(List.of(query));
                updateMessage("执行余弦相似度检索...");
                List<SearchResult> results = storageDriver.searchSimilar(
                        targetNameField.getText().trim(), queryVecs.get(0), topK, minScore);

                runOnUi(() -> {
                    searchList.setAll(results);
                    logInfo("检索召回完毕，共匹配到相似段落 " + results.size() + " 条。");
                });
                updateMessage("查询结束");
                return null;
            }
        };

        progressStatusLabel.textProperty().bind(searchTask.messageProperty());
        startTask(searchTask, "rag-search");
    }

    @FXML
    public void onGenerateExportCode() {
        String lang = exportLangCombo.getValue();
        String dbType = storageTypeCombo.getValue();
        String targetName = targetNameField.getText().trim();
        int dim = parseInteger(dimensionField.getText(), 768);
        String query = searchQueryField.getText() != null ? searchQueryField.getText() : "示例查询文本";

        String code;
        if ("PYTHON".equalsIgnoreCase(lang)) {
            code = SearchCodeGenerator.generatePythonCode(dbType, targetName, dim, query);
        } else if ("JAVA".equalsIgnoreCase(lang)) {
            code = SearchCodeGenerator.generateJavaCode(dbType, targetName, dim);
        } else {
            code = SearchCodeGenerator.generateCurlCode(18088, query, parseInteger(topKField.getText(), 5));
        }
        codeExportArea.setText(code);
    }

    @FXML
    public void onToggleRestGateway(ActionEvent event) {
        try {
            if (restGateway.isRunning()) {
                restGateway.stop();
                toggleRestBtn.setText("启动本地 REST API (18088)");
                logInfo("内置 HTTP REST 网关已停止。");
            } else {
                EmbeddingEngine embEngine = buildEmbeddingEngine();
                VectorStorageDriver driver = buildStorageDriver();
                restGateway.start(18088, embEngine, driver, targetNameField.getText().trim());
                toggleRestBtn.setText("停止 REST API (已监听 18088)");
                logInfo("内置 HTTP REST 网关已启动监听于 http://127.0.0.1:18088");
            }
        } catch (Exception e) {
            showAlert("服务异常", "切换 REST API 状态出错: " + e.getMessage());
        }
    }

    private EmbeddingEngine buildEmbeddingEngine() {
        int dim = parseInteger(dimensionField.getText(), 768);
        String model = modelNameField.getText().trim();
        if (modeLocalRadio.isSelected()) {
            return new LocalOfflineEmbeddingEngine(dim, model);
        } else {
            return new RemoteApiEmbeddingEngine(apiUrlField.getText().trim(), apiKeyField.getText(), model, dim);
        }
    }

    private VectorStorageDriver buildStorageDriver() {
        String type = storageTypeCombo.getValue();
        String conn = dbConnStringField.getText().trim();
        if ("FAISS_LOCAL".equalsIgnoreCase(type)) {
            return new FaissLocalVectorDriver(conn.isEmpty() ? "rag_faiss.index" : conn);
        } else if ("MILVUS".equalsIgnoreCase(type)) {
            return new MilvusVectorDriver(conn.isEmpty() ? "localhost:19530" : conn, apiKeyField != null ? apiKeyField.getText() : "");
        } else if ("PGVECTOR".equalsIgnoreCase(type)) {
            return new PgVectorDriver(conn, "postgres", "password");
        } else if ("ELASTICSEARCH".equalsIgnoreCase(type)) {
            return new ElasticsearchVectorDriver(conn, apiKeyField != null ? apiKeyField.getText() : "");
        } else {
            return new SqliteVectorDriver(conn.isEmpty() ? "rag_kb.db" : conn);
        }
    }

    private void applyWorkspaceProfile(KbWorkspaceProfile p) {
        if (p == null) return;
        if (filePathField != null) filePathField.setText(p.getSourceFilePath() != null ? p.getSourceFilePath() : "");
        if (foldBlankCheck != null) foldBlankCheck.setSelected(p.isFoldBlankLines());
        if (trimSpaceCheck != null) trimSpaceCheck.setSelected(p.isTrimSpaces());
        if (excludeRegexField != null) excludeRegexField.setText(p.getExcludeRegex() != null ? p.getExcludeRegex() : "");
        if (strategyCombo != null && p.getStrategyName() != null) strategyCombo.setValue(p.getStrategyName());
        if (customDelimiterField != null) customDelimiterField.setText(p.getCustomDelimiter() != null ? p.getCustomDelimiter() : "");
        if (chunkSizeField != null) chunkSizeField.setText(String.valueOf(p.getChunkSize()));
        if (overlapField != null) overlapField.setText(String.valueOf(p.getOverlapSize()));

        if (p.isApiMode()) {
            if (modeApiRadio != null) modeApiRadio.setSelected(true);
        } else {
            if (modeLocalRadio != null) modeLocalRadio.setSelected(true);
        }
        if (modelNameField != null) modelNameField.setText(p.getModelName() != null ? p.getModelName() : "text-embedding-3-small");
        if (dimensionField != null) dimensionField.setText(String.valueOf(p.getDimension() > 0 ? p.getDimension() : 768));
        if (apiUrlField != null) apiUrlField.setText(p.getApiUrl() != null ? p.getApiUrl() : "");

        if (storageTypeCombo != null && p.getStorageType() != null) storageTypeCombo.setValue(p.getStorageType());
        if (targetNameField != null) targetNameField.setText(p.getTargetTableName() != null ? p.getTargetTableName() : "kb_chunks");
        if (dbConnStringField != null) dbConnStringField.setText(p.getConnectionStringOrPath() != null ? p.getConnectionStringOrPath() : "");
        if (enableIncrementalCheck != null) enableIncrementalCheck.setSelected(p.isIncrementalCheck());
        logInfo("已切换至知识库工作空间节点: " + p.getProfileName());
    }

    @FXML
    public void onSaveProfile() {
        TextInputDialog dialog = new TextInputDialog("新知识库项目节点");
        dialog.setTitle("保存知识库节点");
        dialog.setHeaderText("请输入知识库项目名称");
        dialog.setContentText("节点名称:");
        dialog.showAndWait().ifPresent(name -> {
            String id = "profile-" + System.currentTimeMillis();
            KbWorkspaceProfile p = new KbWorkspaceProfile(
                    id, name,
                    filePathField.getText(),
                    foldBlankCheck != null && foldBlankCheck.isSelected(),
                    trimSpaceCheck != null && trimSpaceCheck.isSelected(),
                    excludeRegexField != null ? excludeRegexField.getText() : "",
                    strategyCombo.getValue(),
                    customDelimiterField != null ? customDelimiterField.getText() : "",
                    parseInteger(chunkSizeField.getText(), 500),
                    parseInteger(overlapField.getText(), 60),
                    modeApiRadio != null && modeApiRadio.isSelected(),
                    modelNameField.getText(),
                    parseInteger(dimensionField.getText(), 768),
                    apiUrlField != null ? apiUrlField.getText() : "",
                    storageTypeCombo.getValue(),
                    targetNameField.getText(),
                    dbConnStringField.getText(),
                    enableIncrementalCheck != null && enableIncrementalCheck.isSelected()
            );
            profileManager.saveOrUpdateProfile(p);
            kbProfileCombo.setItems(FXCollections.observableArrayList(profileManager.getProfiles()));
            kbProfileCombo.setValue(p);
            logInfo("成功新增知识库工作空间节点: " + name);
        });
    }

    @FXML
    public void onDeleteProfile() {
        KbWorkspaceProfile p = kbProfileCombo.getValue();
        if (p == null) return;
        profileManager.deleteProfile(p.getProfileId());
        kbProfileCombo.setItems(FXCollections.observableArrayList(profileManager.getProfiles()));
        if (!kbProfileCombo.getItems().isEmpty()) {
            kbProfileCombo.getSelectionModel().select(0);
        }
        logInfo("已删除节点: " + p.getProfileName());
    }

    @FXML
    public void onBrowseDbPath() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择本地数据库或快照文件路径");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("数据或索引文件 (*.db, *.index, *.*)", "*.db", "*.index", "*.*")
        );
        File file = chooser.showOpenDialog(null);
        if (file != null) {
            dbConnStringField.setText(file.getAbsolutePath());
            logInfo("已选定本地数据库路径: " + file.getAbsolutePath());
        }
    }

    @FXML
    public void onRefreshDbExplorer(ActionEvent event) {
        Task<List<DocumentChunk>> task = new Task<>() {
            @Override
            protected List<DocumentChunk> call() throws Exception {
                VectorStorageDriver driver = buildStorageDriver();
                String filter = dbSearchField != null ? dbSearchField.getText() : "";
                return driver.listStoredChunks(targetNameField.getText().trim(), filter, 500);
            }
        };
        task.setOnSucceeded(e -> {
            List<DocumentChunk> list = task.getValue();
            dbExplorerList.setAll(list);
            if (dbStatusLabel != null) {
                dbStatusLabel.setText("已载入 " + list.size() + " 条目标库现存切片资产。");
            }
            logInfo("在库切片资产检索完成，共载入 " + list.size() + " 条记录。");
        });
        task.setOnFailed(e -> {
            logInfo("读取在库切片异常: " + task.getException().getMessage());
            if (dbStatusLabel != null) {
                dbStatusLabel.setText("查询异常: " + task.getException().getMessage());
            }
        });
        startTask(task, "rag-explore-db");
    }

    @FXML
    public void onOpenDbEditDialog() {
        DocumentChunk selected = dbExplorerTableView != null ? dbExplorerTableView.getSelectionModel().getSelectedItem() : null;
        if (selected == null) {
            showAlert("提示", "请先在看板表格中单击选中一条需要修整校对的切片记录！");
            return;
        }
        openChunkEditModalDialog(selected);
    }

    @FXML
    public void onDeleteDbRow(ActionEvent event) {
        DocumentChunk selected = dbExplorerTableView != null ? dbExplorerTableView.getSelectionModel().getSelectedItem() : null;
        if (selected == null) {
            showAlert("提示", "请先选择需要彻底从数据库删除的切片纪录！");
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                VectorStorageDriver driver = buildStorageDriver();
                driver.deleteChunkById(targetNameField.getText().trim(), selected.getChunkId());
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            dbExplorerList.remove(selected);
            if (dbStatusLabel != null) {
                dbStatusLabel.setText("已物理彻底剔除该切片，当前剩余 " + dbExplorerList.size() + " 条");
            }
            logInfo("已从向量数据库中彻底物理删除切片: " + selected.getChunkId());
        });
        startTask(task, "rag-delete-db");
    }

    private ChunkerEngine.Strategy resolveStrategy(String label) {
        if (label != null) {
            for (ChunkerEngine.Strategy s : ChunkerEngine.Strategy.values()) {
                if (label.equals(s.getLabel()) || label.contains(s.name())) {
                    return s;
                }
            }
        }
        return ChunkerEngine.Strategy.RECURSIVE_SEMANTIC;
    }

    private int parseInteger(String val, int def) {
        try { return Integer.parseInt(val.trim()); } catch (Exception e) { return def; }
    }

    private double parseDouble(String val, double def) {
        try { return Double.parseDouble(val.trim()); } catch (Exception e) { return def; }
    }

    private void logInfo(String msg) {
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String line = "[" + timeStr + "] [INFO] " + msg + "\n";
        runOnUi(() -> {
            consoleArea.appendText(line);
        });
    }

    private void showAlert(String title, String content) {
        runOnUi(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    private void startTask(Task<?> task, String threadName) {
        if (disposed) return;
        runningTasks.add(task);
        Thread thread = new Thread(() -> {
            try { task.run(); } finally { runningTasks.remove(task); }
        }, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    private void runOnUi(Runnable action) {
        if (!disposed) Platform.runLater(() -> { if (!disposed) action.run(); });
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        for (Task<?> task : new ArrayList<>(runningTasks)) {
            try { task.cancel(true); } catch (RuntimeException ignored) { }
        }
        runningTasks.clear();
        try { restGateway.stop(); } catch (RuntimeException ignored) { }
    }
}
