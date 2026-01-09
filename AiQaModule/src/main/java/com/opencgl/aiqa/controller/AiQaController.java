package com.opencgl.aiqa.controller;

import com.opencgl.aiqa.model.AiConfig;
import com.opencgl.aiqa.model.ChatMessage;
import com.opencgl.aiqa.model.ModelConfig;
import com.opencgl.aiqa.provider.AiProvider;
import com.opencgl.aiqa.provider.OpenAiProvider;
import com.opencgl.aiqa.i18n.I18N;
import com.opencgl.aiqa.service.DocumentLibraryService;
import com.opencgl.aiqa.service.ModelConfigService;
import com.opencgl.aiqa.views.AiQaView;
import com.opencgl.base.utils.DialogUtil;
import com.opencgl.base.utils.TooltipUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.prefs.Preferences;

/**
 * AI Q&A Controller
 */
public class AiQaController extends AiQaView {
    private static final Logger logger = LoggerFactory.getLogger(AiQaController.class);

    private final ModelConfigService modelConfigService = new ModelConfigService();
    private final DocumentLibraryService docService = new DocumentLibraryService();
    private final Preferences prefs = Preferences.userNodeForPackage(AiQaController.class);

    private AiProvider currentProvider;
    private ModelConfig selectedModel;
    private final List<ChatMessage> chatHistory = new ArrayList<>();

    // 流式响应状态
    private TextArea currentThinkingArea;
    private WebView currentResponseWebView;
    private TitledPane currentThinkingPane;
    private StringBuilder currentThinkingText = new StringBuilder();
    private StringBuilder currentResponseText = new StringBuilder();
    private AtomicBoolean isStreaming = new AtomicBoolean(false);
    private AtomicBoolean stopRequested = new AtomicBoolean(false);
    private VBox currentMessageBox;
    private final Text textHelper = new Text();
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ai-qa-controller");
        thread.setDaemon(true);
        return thread;
    });
    private Future<?> documentWatchFuture;
    private volatile boolean disposed;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initModelList();
        initButtons();
        initInputArea();
        loadDocPath();

        // 监听内容高度变化自动滚动到底部
        chatContainer.heightProperty().addListener((obs, oldVal, newVal) -> {
            if (isStreaming.get()) {
                chatScrollPane.setVvalue(1.0);
            }
        });

        // 监听窗口宽度变化，重新计算所有 WebView 的高度
        chatContainer.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (oldVal.doubleValue() > 0 && Math.abs(newVal.doubleValue() - oldVal.doubleValue()) > 10) {
                recalculateAllWebViewHeights();
            }
        });
        initI18n();
    }

    private void initI18n() {
        if (modelListLabel != null) modelListLabel.textProperty().bind(I18N.getBinding("label.model_list"));
        configPane.textProperty().bind(I18N.getBinding("label.model_config"));
        docPane.textProperty().bind(I18N.getBinding("label.doc_lib"));
        // sendBtn: toggles between Send/Stop at runtime, use setText(I18N.get(...))
        clearChatBtn.textProperty().bind(I18N.getBinding("button.clear"));
    }

    /**
     * 重新计算聊天区域中所有 WebView 的高度
     */
    private void recalculateAllWebViewHeights() {
        Platform.runLater(() -> {
            if (disposed) return;
            for (javafx.scene.Node node : chatContainer.getChildren()) {
                if (node instanceof HBox) {
                    HBox container = (HBox) node;
                    for (javafx.scene.Node child : container.getChildren()) {
                        if (child instanceof VBox) {
                            VBox messageBox = (VBox) child;
                            for (javafx.scene.Node msgChild : messageBox.getChildren()) {
                                if (msgChild instanceof WebView) {
                                    WebView webView = (WebView) msgChild;
                                    try {
                                        Object result = webView.getEngine().executeScript(
                                                "(document.getElementById('content').style.display !== 'none' ? " +
                                                        "document.getElementById('content').offsetHeight : " +
                                                        "document.getElementById('rawContent').offsetHeight)");
                                        if (result instanceof Number) {
                                            double contentHeight = ((Number) result).doubleValue();
                                            double height = Math.min(8000, Math.max(60, contentHeight + 28 + 28 + 16));
                                            webView.setPrefHeight(height);
                                            webView.setMinHeight(height);
                                        }
                                    } catch (Exception ignored) {
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    // ==================== 模型列表管理 ====================

    private void initModelList() {
        // 加载模型列表
        refreshModelList();

        // 选择事件
        modelListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                onModelSelected(newVal);
            }
        });

        // 默认选中第一个
        if (!modelListView.getItems().isEmpty()) {
            modelListView.getSelectionModel().selectFirst();
        }
    }

    private void refreshModelList() {
        modelListView.getItems().clear();
        for (ModelConfig config : modelConfigService.getConfigs()) {
            modelListView.getItems().add(config.getName());
        }
    }

    private void onModelSelected(String name) {
        List<ModelConfig> configs = modelConfigService.getConfigs();
        for (ModelConfig config : configs) {
            if (config.getName().equals(name)) {
                selectedModel = config;
                applyModelConfig(config);
                break;
            }
        }
    }

    private void applyModelConfig(ModelConfig config) {
        baseUrlField.setText(config.getBaseUrl());
        apiKeyField.setText(config.getApiKey());
        modelField.setText(config.getModel());
        temperatureField.setText(String.valueOf(config.getTemperature()));

        // 配置 Provider
        AiProvider previousProvider = currentProvider;
        currentProvider = new OpenAiProvider();
        if (previousProvider != null) {
            try {
                previousProvider.close();
            } catch (RuntimeException e) {
                logger.warn("Failed to close previous AI provider", e);
            }
        }
        AiConfig aiConfig = AiConfig.builder()
                .providerType(AiConfig.ProviderType.OPENAI)
                .openaiBaseUrl(config.getBaseUrl())
                .openaiApiKey(config.getApiKey())
                .openaiModel(config.getModel())
                .temperature(config.getTemperature())
                .build();
        currentProvider.configure(aiConfig);

        // 配置文档库 Embedding
        docService.configureEmbedding(config.getBaseUrl(), config.getApiKey());
    }

    private void initButtons() {
        // 模型管理按钮
        addModelBtn.setOnAction(e -> showAddModelDialog());
        editModelBtn.setOnAction(e -> showEditModelDialog());
        deleteModelBtn.setOnAction(e -> deleteSelectedModel());
        saveModelBtn.setOnAction(e -> saveCurrentModel());

        // 连接测试
        testConnectionBtn.setOnAction(e -> testConnection());

        // 清空聊天
        clearChatBtn.setOnAction(e -> clearChat());

        // 发送
        sendBtn.setOnAction(e -> handleSendOrStop());

        // 文档库
        browseDocBtn.setOnAction(e -> browseDocumentFolder());
    }

    private void showAddModelDialog() {
        Dialog<ModelConfig> dialog = createModelDialog(null);
        Optional<ModelConfig> result = dialog.showAndWait();
        result.ifPresent(config -> {
            modelConfigService.addConfig(config);
            refreshModelList();
            modelListView.getSelectionModel().select(config.getName());
        });
    }

    private void showEditModelDialog() {
        if (selectedModel == null)
            return;

        Dialog<ModelConfig> dialog = createModelDialog(selectedModel);
        Optional<ModelConfig> result = dialog.showAndWait();
        result.ifPresent(config -> {
            modelConfigService.updateConfig(config);
            refreshModelList();
            modelListView.getSelectionModel().select(config.getName());
        });
    }

    private Dialog<ModelConfig> createModelDialog(ModelConfig existing) {
        Dialog<ModelConfig> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "新增模型" : "编辑模型");

        ButtonType saveButtonType = new ButtonType("保存", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nameField = new TextField(existing != null ? existing.getName() : "新模型");
        TextField urlField = new TextField(existing != null ? existing.getBaseUrl() : "http://localhost:8181");
        TextField keyField = new TextField(existing != null ? existing.getApiKey() : "");
        TextField modelNameField = new TextField(existing != null ? existing.getModel() : "deepseek-r1");
        TextField tempField = new TextField(existing != null ? String.valueOf(existing.getTemperature()) : "0.7");

        grid.add(new Label("名称:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("API 地址:"), 0, 1);
        grid.add(urlField, 1, 1);
        grid.add(new Label("API Key:"), 0, 2);
        grid.add(keyField, 1, 2);
        grid.add(new Label("模型:"), 0, 3);
        grid.add(modelNameField, 1, 3);
        grid.add(new Label("温度:"), 0, 4);
        grid.add(tempField, 1, 4);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                return ModelConfig.builder()
                        .id(existing != null ? existing.getId() : UUID.randomUUID().toString())
                        .name(nameField.getText())
                        .baseUrl(urlField.getText())
                        .apiKey(keyField.getText())
                        .model(modelNameField.getText())
                        .temperature(parseDouble(tempField.getText(), 0.7))
                        .build();
            }
            return null;
        });

        return dialog;
    }

    private void deleteSelectedModel() {
        if (selectedModel == null)
            return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(I18N.get("title.delete_confirm"));
        confirm.setHeaderText(I18N.get("msg.delete_model_confirm", selectedModel.getName()));

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            modelConfigService.deleteConfig(selectedModel.getId());
            refreshModelList();
            if (!modelListView.getItems().isEmpty()) {
                modelListView.getSelectionModel().selectFirst();
            }
        }
    }

    /**
     * 保存当前表单值到选中的模型
     */
    private void saveCurrentModel() {
        if (selectedModel == null)
            return;

        selectedModel.setBaseUrl(baseUrlField.getText());
        selectedModel.setApiKey(apiKeyField.getText());
        selectedModel.setModel(modelField.getText());
        selectedModel.setTemperature(parseDouble(temperatureField.getText(), 0.7));

        modelConfigService.updateConfig(selectedModel);
        TooltipUtil.showToast(mainStackPane, I18N.get("msg.saved_config"));
    }

    // ==================== 文档库 ====================

    private void loadDocPath() {
        String savedPath = prefs.get("docPath", "");
        if (!savedPath.isEmpty()) {
            docPathField.setText(savedPath);
            loadDocuments(savedPath);
        }
    }

    private void browseDocumentFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(I18N.get("title.choose_doc_dir"));
        java.io.File dir = chooser.showDialog(mainStackPane.getScene().getWindow());
        if (dir != null) {
            String path = dir.getAbsolutePath();
            docPathField.setText(path);
            prefs.put("docPath", path);
            loadDocuments(path);
        }
    }

    private void loadDocuments(String path) {
        if (selectedModel != null) {
            docService.configureEmbedding(selectedModel.getBaseUrl(), selectedModel.getApiKey());
        }

        int count = docService.loadDocuments(path);
        if (count > 0) {
            int chunks = docService.getChunkCount();
            docCountLabel.setText(I18N.get("msg.doc_indexing", String.valueOf(count), String.valueOf(chunks)));
            docCountLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #1976d2;");

            if (documentWatchFuture != null) documentWatchFuture.cancel(true);
            documentWatchFuture = backgroundExecutor.submit(() -> {
                while (!disposed && !docService.isIndexed()) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                Platform.runLater(() -> {
                    if (disposed) return;
                    docCountLabel.setText(I18N.get("msg.doc_indexed", String.valueOf(count)));
                    docCountLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #2e7d32;");
                });
            });
        } else {
            docCountLabel.setText(I18N.get("msg.doc_not_found"));
            docCountLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #f57c00;");
        }
    }

    // ==================== 连接测试 ====================

    private void testConnection() {
        if (currentProvider == null)
            return;

        testConnectionBtn.setDisable(true);
        currentProvider.testConnection().thenAccept(success -> {
            Platform.runLater(() -> {
                if (disposed) return;
                testConnectionBtn.setDisable(false);
                if (success) {
                    TooltipUtil.showToast(mainStackPane, I18N.get("msg.conn_ok"));
                } else {
                    DialogUtil.showErrorInfo(I18N.get("msg.conn_fail_check"), mainStackPane);
                }
            });
        });
    }

    // ==================== 聊天功能 ====================

    private void initInputArea() {
        inputTextArea.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                if (e.isAltDown()) {
                    inputTextArea.insertText(inputTextArea.getCaretPosition(), "\n");
                } else {
                    e.consume();
                    handleSendOrStop();
                }
            }
        });
    }

    private void handleSendOrStop() {
        if (isStreaming.get()) {
            stopRequested.set(true);
            sendBtn.setText(I18N.get("button.send"));
            isStreaming.set(false);
        } else {
            sendMessage();
        }
    }

    private void clearChat() {
        chatHistory.clear();
        chatContainer.getChildren().clear();
    }

    private void sendMessage() {
        String userInput = inputTextArea.getText().trim();
        if (userInput.isEmpty() || currentProvider == null)
            return;

        inputTextArea.clear();
        addUserMessageToUI(userInput);

        // RAG: 检索相关文档
        List<ChatMessage> messagesWithContext = new ArrayList<>();
        if (docService.hasDocuments()) {
            String relevantDocs = docService.searchRelevant(userInput, 5);
            if (!relevantDocs.isEmpty()) {
                String systemPrompt = "以下是与问题相关的参考文档：\n" + relevantDocs;
                messagesWithContext.add(ChatMessage.system(systemPrompt));
            }
        }
        messagesWithContext.addAll(chatHistory);
        messagesWithContext.add(ChatMessage.user(userInput));

        chatHistory.add(ChatMessage.user(userInput));
        createAssistantMessageUI();

        isStreaming.set(true);
        stopRequested.set(false);
        sendBtn.setText(I18N.get("button.stop"));

        currentProvider.chatStream(
                messagesWithContext,
                token -> {
                    if (disposed || stopRequested.get())
                        return;
                    Platform.runLater(() -> {
                        if (!disposed) appendToken(token);
                    });
                },
                () -> Platform.runLater(() -> {
                    if (!disposed) onStreamComplete();
                }),
                error -> Platform.runLater(() -> {
                    if (!disposed) onStreamError(error);
                }));
    }

    private void addUserMessageToUI(String message) {
        VBox messageBox = new VBox(8);
        messageBox.setPadding(new Insets(12));
        messageBox.setStyle(
                "-fx-background-color: linear-gradient(to right, #e3f2fd, #bbdefb); -fx-background-radius: 12;");

        Label roleLabel = new Label(I18N.get("label.you_display"));
        roleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1565c0; -fx-font-size: 13px;");

        TextArea contentArea = new TextArea(message);
        contentArea.setWrapText(true);
        contentArea.setEditable(false);
        contentArea.setStyle(
                "-fx-control-inner-background: transparent; -fx-background-color: transparent; -fx-text-fill: #1a1a1a;");

        // 计算高度
        textHelper.setText(message);
        textHelper.setWrappingWidth(650);
        double height = Math.max(40, textHelper.getLayoutBounds().getHeight() + 20);
        contentArea.setPrefHeight(height);
        contentArea.setMinHeight(height);

        messageBox.getChildren().addAll(roleLabel, contentArea);

        HBox container = new HBox(messageBox);
        container.setAlignment(Pos.CENTER_RIGHT);
        container.setPadding(new Insets(5, 0, 5, 50));
        chatContainer.getChildren().add(container);
        scrollToBottom();
    }

    private void createAssistantMessageUI() {
        currentThinkingText = new StringBuilder();
        currentResponseText = new StringBuilder();

        VBox messageBox = new VBox(10);
        messageBox.setPadding(new Insets(12));
        messageBox.setStyle(
                "-fx-background-color: linear-gradient(to right, #f5f5f5, #e8e8e8); -fx-background-radius: 12;");

        Label roleLabel = new Label(I18N.get("label.ai_display"));
        roleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2e7d32; -fx-font-size: 13px;");

        currentThinkingArea = new TextArea();
        currentThinkingArea.setWrapText(true);
        currentThinkingArea.setEditable(false);
        currentThinkingArea.setStyle("-fx-control-inner-background: #fff8e1; -fx-font-style: italic;");
        currentThinkingArea.setMinHeight(50);

        currentThinkingPane = new TitledPane("💭 思考过程", currentThinkingArea);
        currentThinkingPane.setExpanded(true);
        currentThinkingPane.setVisible(false);
        currentThinkingPane.setManaged(false);

        currentResponseWebView = new WebView();
        currentResponseWebView.setPrefHeight(60);
        currentResponseWebView.getEngine().loadContent(getBaseHtml());

        // JS 切换格式后重新计算高度的桥接
        final WebView webView = currentResponseWebView;
        currentResponseWebView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                netscape.javascript.JSObject window = (netscape.javascript.JSObject) webView.getEngine()
                        .executeScript("window");
                window.setMember("javaConnector", new JavaConnector(webView, chatScrollPane, chatContainer));
            }
        });

        // 将 WebView 的滚轮事件传递给父 ScrollPane
        currentResponseWebView.setOnScroll(event -> {
            double deltaY = event.getDeltaY();
            double currentVvalue = chatScrollPane.getVvalue();
            double newVvalue = currentVvalue - (deltaY / chatContainer.getHeight() * 3);
            chatScrollPane.setVvalue(Math.max(0, Math.min(1, newVvalue)));
            event.consume();
        });

        messageBox.getChildren().addAll(roleLabel, currentThinkingPane, currentResponseWebView);
        currentMessageBox = messageBox;

        HBox container = new HBox(messageBox);
        container.setAlignment(Pos.CENTER_LEFT);
        container.setPadding(new Insets(5, 50, 5, 0));
        chatContainer.getChildren().add(container);
    }

    private void appendToken(String token) {
        if (token.startsWith("💭")) {
            String thinking = token.substring(2);
            currentThinkingText.append(thinking);

            if (!currentThinkingPane.isVisible()) {
                currentThinkingPane.setVisible(true);
                currentThinkingPane.setManaged(true);
            }

            currentThinkingArea.setText(currentThinkingText.toString());
            currentThinkingArea.positionCaret(currentThinkingText.length());
        } else {
            currentResponseText.append(token);
            String escaped = currentResponseText.toString()
                    .replace("\\", "\\\\")
                    .replace("`", "\\`")
                    .replace("'", "\\'")
                    .replace("\n", "\\n");

            try {
                currentResponseWebView.getEngine().executeScript("updateContent('" + escaped + "')");

                // 获取 content 的高度，加上固定的 header(28) + footer(28) + mainContent padding(16)
                Object result = currentResponseWebView.getEngine().executeScript(
                        "document.getElementById('content').offsetHeight");
                if (result instanceof Number) {
                    // content高度 + header(28) + footer(28) + mainContent padding(16)
                    double contentHeight = ((Number) result).doubleValue();
                    double height = Math.min(8000, Math.max(60, contentHeight + 28 + 28 + 16));
                    currentResponseWebView.setPrefHeight(height);
                    currentResponseWebView.setMinHeight(height);
                }
            } catch (Exception e) {
                currentResponseWebView.getEngine().loadContent(getMarkdownHtml(currentResponseText.toString()));
            }
        }
        scrollToBottom();
    }

    private void onStreamComplete() {
        isStreaming.set(false);
        sendBtn.setText(I18N.get("button.send"));

        if (currentThinkingPane != null && currentThinkingPane.isVisible()) {
            currentThinkingPane.setExpanded(false);
        }

        String fullResponse = currentResponseText.toString();
        if (!fullResponse.isEmpty()) {
            chatHistory.add(ChatMessage.assistant(fullResponse));
        }

        // 用 JS 获取实际高度并调整 WebView，显示切换按钮
        if (currentResponseWebView != null) {
            final WebView webView = currentResponseWebView;
            // 延迟执行，确保 DOM 完全渲染
            Platform.runLater(() -> {
                try {
                    Thread.sleep(150);
                } catch (InterruptedException ignored) {
                }

                Platform.runLater(() -> {
                    try {
                        // 显示格式切换按钮
                        webView.getEngine().executeScript("showToggle()");

                        // 重新注册 JS 桥接（确保切换时可用）
                        netscape.javascript.JSObject window = (netscape.javascript.JSObject) webView.getEngine()
                                .executeScript("window");
                        window.setMember("javaConnector", new JavaConnector(webView, chatScrollPane, chatContainer));

                        // 计算正确高度: content + header(28) + footer(28) + padding(16)
                        Object result = webView.getEngine().executeScript(
                                "document.getElementById('content').offsetHeight");
                        if (result instanceof Number) {
                            double contentHeight = ((Number) result).doubleValue();
                            double height = Math.min(8000, Math.max(60, contentHeight + 28 + 28 + 16));
                            webView.setPrefHeight(height);
                            webView.setMinHeight(height);
                        }
                    } catch (Exception e) {
                        logger.error("调整高度失败", e);
                    }
                    scrollToBottom();
                });
            });
        }
    }

    private void onStreamError(Throwable error) {
        isStreaming.set(false);
        sendBtn.setText(I18N.get("button.send"));
        logger.error("AI 请求失败", error);
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            if (disposed) return;
            chatContainer.applyCss();
            chatContainer.layout();
            chatScrollPane.applyCss();
            chatScrollPane.layout();
            chatScrollPane.setVvalue(1.0);

            // 延迟再滚动一次确保到底部
            Platform.runLater(() -> {
                if (!disposed) chatScrollPane.setVvalue(1.0);
            });
        });
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        stopRequested.set(true);
        isStreaming.set(false);
        if (documentWatchFuture != null) documentWatchFuture.cancel(true);
        backgroundExecutor.shutdownNow();
        if (currentProvider != null) {
            try {
                currentProvider.close();
            } catch (RuntimeException e) {
                logger.warn("Failed to close AI provider", e);
            } finally {
                currentProvider = null;
            }
        }
        try {
            docService.close();
        } catch (RuntimeException e) {
            logger.warn("Failed to close document library", e);
        }
        if (chatContainer != null) {
            for (javafx.scene.Node node : chatContainer.lookupAll(".web-view")) {
                if (node instanceof WebView webView) {
                    try {
                        webView.getEngine().load(null);
                        webView.setOnScroll(null);
                    } catch (RuntimeException e) {
                        logger.warn("Failed to clear chat WebView", e);
                    }
                }
            }
            chatContainer.getChildren().clear();
        }
        currentResponseWebView = null;
    }

    private double parseDouble(String text, double defaultValue) {
        try {
            return Double.parseDouble(text);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String getBaseHtml() {
        // 从 classpath 加载本地资源
        String markedJs = getResourcePath("/com/opencgl/aiqa/libs/js/marked.min.js");

        return """
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8">
                <script src="%s"></script>
                <style>""".formatted(markedJs)
                + """
                            html, body { overflow: hidden; margin: 0; padding: 0; color: #1a1a1a; }
                            body { font-family: -apple-system, sans-serif; font-size: 14px; line-height: 1.6; padding: 0; }
                            #header { height: 28px; display: flex; align-items: center; justify-content: flex-end; padding: 0 8px; }
                            #mainContent { padding: 8px; }
                            h1,h2,h3,h4,h5,h6 { margin: 12px 0 8px 0; color: #1a1a1a !important; }
                            h2 { color: #1a1a1a !important; background: transparent !important; }
                            p, li, td, th, span, a { color: #1a1a1a; }
                            img { max-width: 100%; }
                            code { background: #f5f5f5; padding: 2px 6px; border-radius: 4px; color: #d63384; }
                            pre { background: #263238; color: #aed581; padding: 12px; border-radius: 6px; overflow-x: auto; }
                            pre code { background: transparent; color: #aed581; }
                            table { border-collapse: collapse; width: 100%; }
                            th, td { border: 1px solid #ddd; padding: 8px; }
                            #toggleBtn { padding: 2px 10px; font-size: 11px; background: #f0f0f0; border: 1px solid #ccc;
                                         border-radius: 4px; cursor: pointer; display: none; }
                            #toggleBtn:hover { background: #e0e0e0; }
                            #rawContent { white-space: pre-wrap; word-break: break-word; font-family: monospace; display: none; }
                            #footer { height: 28px; }
                        </style>
                        </head><body>
                        <div id="header"><button id="toggleBtn" onclick="toggleFormat()">Raw</button></div>
                        <div id="mainContent">
                            <div id="content"></div>
                            <div id="rawContent"></div>
                        </div>
                        <div id="footer"></div>
                        <script>
                            var rawMarkdown = '';
                            var showingRaw = false;
                            function updateContent(md) {
                                rawMarkdown = md;
                                try { document.getElementById('content').innerHTML = marked.parse(md); }
                                catch(e) { document.getElementById('content').innerText = md; }
                                document.getElementById('rawContent').innerText = md;
                            }
                            function showToggle() {
                                document.getElementById('toggleBtn').style.display = 'block';
                            }
                            function toggleFormat() {
                                showingRaw = !showingRaw;
                                document.getElementById('content').style.display = showingRaw ? 'none' : 'block';
                                document.getElementById('rawContent').style.display = showingRaw ? 'block' : 'none';
                                document.getElementById('toggleBtn').innerText = showingRaw ? 'Markdown' : 'Raw';
                                // 等待 DOM 完全更新后通知 JavaFX 重新计算高度
                                setTimeout(function() {
                                    if (window.javaConnector) window.javaConnector.recalculateHeight();
                                }, 100);
                            }
                            function getTotalHeight() {
                                return document.body.scrollHeight;
                            }
                        </script>
                        </body></html>
                        """;
    }

    private String getMarkdownHtml(String markdown) {
        String escaped = markdown.replace("\\", "\\\\").replace("`", "\\`");
        return getBaseHtml().replace("<div id=\"content\"></div>",
                "<div id=\"content\"></div><script>updateContent(`" + escaped + "`);</script>");
    }

    /**
     * JS 回调的桥接类，用于切换格式后重新计算高度
     */
    public static class JavaConnector {
        private final WebView webView;
        private final ScrollPane scrollPane;
        private final VBox container;

        public JavaConnector(WebView webView, ScrollPane scrollPane, VBox container) {
            this.webView = webView;
            this.scrollPane = scrollPane;
            this.container = container;
        }

        public void recalculateHeight() {
            // 延迟执行，确保 DOM 已经完全更新
            Platform.runLater(() -> {
                try {
                    Thread.sleep(200); // 增加延迟确保 raw 内容完全渲染
                } catch (InterruptedException ignored) {
                }

                Platform.runLater(() -> {
                    try {
                        // 根据当前显示的是 content 还是 rawContent 获取高度
                        Object result = webView.getEngine().executeScript(
                                "(document.getElementById('content').style.display !== 'none' ? " +
                                        "document.getElementById('content').offsetHeight : " +
                                        "document.getElementById('rawContent').offsetHeight)");
                        if (result instanceof Number) {
                            // content高度 + header(28) + footer(28) + mainContent padding(16)
                            double contentHeight = ((Number) result).doubleValue();
                            double height = Math.min(8000, Math.max(60, contentHeight + 28 + 28 + 16));
                            webView.setPrefHeight(height);
                            webView.setMinHeight(height);

                            container.layout();
                            scrollPane.layout();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            });
        }
    }

    /**
     * 获取资源路径（兼容 JAR 包）
     */
    private String getResourcePath(String resourcePath) {
        try {
            var url = getClass().getResource(resourcePath);
            if (url != null) {
                return url.toExternalForm();
            } else {
                System.err.println("Resource not found: " + resourcePath);
                return "";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
}
