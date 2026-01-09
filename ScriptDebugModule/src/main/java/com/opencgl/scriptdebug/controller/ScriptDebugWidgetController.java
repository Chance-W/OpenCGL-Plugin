package com.opencgl.scriptdebug.controller;

import com.alibaba.fastjson.JSON;
import com.opencgl.base.ViewControllerUtil.ThemeSwitchUtil;
import com.opencgl.base.controls.CustomTextArea;
import com.opencgl.base.hook.HookContext;
import com.opencgl.base.utils.OperationHisRecord;
import com.opencgl.base.service.TreeOperateService;
import com.opencgl.base.utils.DialogUtil;
import com.opencgl.base.utils.TooltipUtil;
import com.opencgl.scriptdebug.i18n.I18N;
import com.opencgl.base.utils.tree.TreeViewBuilder;
import com.opencgl.base.view.CustomizeTreeItem;
import com.opencgl.scriptdebug.dao.ScriptDebugWidgetDao;
import com.opencgl.scriptdebug.model.ScriptDebugTreeItem;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXComboBox;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScriptDebugWidgetController implements Initializable, TreeOperateService<ScriptDebugTreeItem> {

    private static final Logger logger = LoggerFactory.getLogger(ScriptDebugWidgetController.class);

    @FXML
    private StackPane mainStackPane;
    @FXML
    private BorderPane contentBorderPane;
    @FXML
    private MFXButton runButton;
    @FXML
    private MFXButton saveButton;
    @FXML
    private MFXButton clearButton;
    @FXML
    private MFXComboBox<String> languageCombo;
    @FXML
    private CustomTextArea scriptEditor;
    @FXML
    private CustomTextArea contextEditor;
    @FXML
    private CustomTextArea outputConsole;

    private final ScriptDebugWidgetDao dao = new ScriptDebugWidgetDao();
    private TreeView<ScriptDebugTreeItem> treeView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "opencgl-script-debug");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean disposed;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initUI();
        initTreeView();
        initI18n();
    }

    private void initI18n() {
        Tooltip runTip = new Tooltip();
        runTip.textProperty().bind(I18N.getBinding("tooltip.run"));
        runButton.setTooltip(runTip);
        Tooltip saveTip = new Tooltip();
        saveTip.textProperty().bind(I18N.getBinding("tooltip.save"));
        saveButton.setTooltip(saveTip);
        Tooltip clearTip = new Tooltip();
        clearTip.textProperty().bind(I18N.getBinding("tooltip.clear"));
        clearButton.setTooltip(clearTip);
    }

    private void initUI() {
        languageCombo.getItems().addAll("Groovy", "JavaScript");
        languageCombo.selectFirst();

        // 设置默认 Context 示例
        contextEditor.setText("{\n  \"arg1\": \"Hello\",\n  \"arg2\": 123\n}");
    }

    private void initTreeView() {
        VBox treeViewVbox = new TreeViewBuilder<ScriptDebugTreeItem>()
            .service(this)
            .enableSearch(true)
            .onTreeCreated(tree -> this.treeView = tree)
            .dataType(ScriptDebugTreeItem.class)
            .enableDragDrop(true)
            .onSelect(this::loadScript)
            .build();

        ThemeSwitchUtil.treeStyleSwitch(mainStackPane, contentBorderPane, treeViewVbox);
    }

    @FXML
    public void runScriptAction() {
        String script = scriptEditor.getText();
        String contextJson = contextEditor.getText();
        String language = languageCombo.getValue();

        if (script == null || script.trim().isEmpty()) {
            DialogUtil.showErrorInfo(I18N.get("msg.scriptEmpty"));
            return;
        }

        outputConsole.replaceText(I18N.get("msg.executing") + "\n");
        runButton.setDisable(true);

        CompletableFuture.runAsync(() -> {
            try {
                ScriptEngineManager manager = new ScriptEngineManager();
                String engineName = "Groovy".equals(language) ? "groovy" : "graal.js";
                // For 'javascript' alias check
                if ("graal.js".equals(engineName)) {
                    // Check if 'js' works or specific name needed
                    if (manager.getEngineByName("graal.js") == null) {
                        engineName = "js"; // Try standard 'js' alias
                    }
                }

                ScriptEngine engine = manager.getEngineByName(engineName);
                if (engine == null) {
                    throw new RuntimeException(I18N.get("msg.engineNotFound", engineName, System.getProperty("java.version")));
                }

                // Redirect Output
                StringWriter outputWriter = new StringWriter();
                PrintWriter printWriter = new PrintWriter(outputWriter);
                engine.getContext().setWriter(printWriter);
                engine.getContext().setErrorWriter(printWriter);

                // Prepare bindings
                Bindings bindings = engine.createBindings();
                bindings.put("console", new ScriptConsole(printWriter));
                HookContext hookContext = new HookContext();
                hookContext.put("history", OperationHisRecord.class);
                bindings.put("context", hookContext);
                bindings.put("scriptLog", hookContext.getScriptLog());
                bindings.put("history", OperationHisRecord.class);

                if (contextJson != null && !contextJson.trim().isEmpty()) {
                    try {
                        Map<String, Object> map = JSON.parseObject(contextJson, Map.class);
                        if (map != null) {
                            bindings.putAll(map);
                        }
                    }
                    catch (Exception e) {
                        throw new IllegalArgumentException(
                                I18N.get("msg.contextParseFailed", e.getMessage()), e);
                    }
                }

                Object result = engine.eval(script, bindings);

                String finalOutput = outputWriter.toString();
                String resultStr = (result != null) ? result.toString() : "null";

                Platform.runLater(() -> {
                    if (disposed) return;
                    outputConsole.clear(); // clear "running..."
                    if (!hookContext.getScriptOutput().isEmpty()) {
                        appendOutput("--- Script Log ---\n");
                        hookContext.getScriptOutput().forEach(line -> appendOutput(line + "\n"));
                        appendOutput("\n");
                    }
                    if (!finalOutput.isEmpty()) {
                        appendOutput("--- Console Output ---\n");
                        appendOutput(finalOutput);
                        appendOutput("\n");
                    }
                    appendOutput("--- Result ---\n");
                    appendOutput(resultStr);
                });

            }
            catch (Exception e) {
                Platform.runLater(() -> {
                    if (disposed) return;
                    outputConsole.replaceText(I18N.get("msg.execError") + "\n" + formatException(e));
                    logger.error("脚本执行失败", e);
                });
            }
            finally {
                Platform.runLater(() -> {
                    if (!disposed) runButton.setDisable(false);
                });
            }
        }, executor);
    }

    private String formatException(Exception exception) {
        StringWriter details = new StringWriter();
        exception.printStackTrace(new PrintWriter(details));
        return details.toString();
    }

    @FXML
    public void saveScriptAction() {
        CustomizeTreeItem<ScriptDebugTreeItem> selected = (CustomizeTreeItem<ScriptDebugTreeItem>) treeView.getSelectionModel().getSelectedItem();
        if (selected == null || !selected.getValue().getIsLeaf()) {
            DialogUtil.showErrorInfo(I18N.get("msg.selectLeaf"));
            return;
        }
        ScriptDebugTreeItem item = selected.getValue();
        item.setLanguage(languageCombo.getValue());
        item.setScriptContent(scriptEditor.getText());
        item.setContextJson(contextEditor.getText());

        try {
            dao.updateData(item);
            TooltipUtil.showToast(mainStackPane, I18N.get("msg.saveSuccess"));
        }
        catch (Exception e) {
            DialogUtil.showErrorInfo(I18N.get("msg.saveFailed", e.getMessage()));
        }
    }

    @FXML
    public void clearAction() {
        outputConsole.clear();
    }

    private void appendOutput(String text) {
        outputConsole.appendText(text);
    }

    private void loadScript(ScriptDebugTreeItem item) {
        if (item == null) return;
        if (Boolean.TRUE.equals(item.getIsLeaf())) {
            if (item.getLanguage() != null) languageCombo.selectItem(item.getLanguage());
            scriptEditor.setText(item.getScriptContent() != null ? item.getScriptContent() : "");
            contextEditor.setText(item.getContextJson() != null ? item.getContextJson() : "");
            outputConsole.clear();
        }
    }

    // --- TreeOperateService Implementation ---

    @Override
    public CustomizeTreeItem<ScriptDebugTreeItem> add(ScriptDebugTreeItem dto) {
        try {
            Long id = dao.insertData(dto);
            dto.setId(id);
            return new CustomizeTreeItem<>(dto);
        }
        catch (Exception e) {
            logger.error("Add failed", e);
            return null;
        }
    }

    @Override
    public CustomizeTreeItem<ScriptDebugTreeItem> importData(ScriptDebugTreeItem dto) {
        return add(dto);
    }

    @Override
    public CustomizeTreeItem<ScriptDebugTreeItem> delete(ScriptDebugTreeItem dto) {
        try {
            dao.delLevelData(dto);
            return new CustomizeTreeItem<>(dto);
        }
        catch (Exception e) {
            logger.error("Delete failed", e);
            return null;
        }
    }

    @Override
    public CustomizeTreeItem<ScriptDebugTreeItem> update(ScriptDebugTreeItem dto) {
        try {
            dao.updateData(dto);
            return new CustomizeTreeItem<>(dto);
        }
        catch (Exception e) {
            logger.error("Update failed", e);
            return null;
        }
    }

    @Override
    public List<ScriptDebugTreeItem> queryAll() {
        try {
            return dao.queryAllData();
        }
        catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Override
    public void updatePositionOnly(ScriptDebugTreeItem dto) {
        try {
            dao.updatePositionOnly(dto);
        }
        catch (Exception e) {
            logger.error("Update position failed", e);
        }
    }

    @Override
    public boolean supportImportAndExport() {
        return true;
    }

    @Override
    public void changeToDisplay(ScriptDebugTreeItem item) {
        loadScript(item);
    }

    // --- Helper Classes ---
    public static class ScriptConsole {
        private final PrintWriter writer;

        public ScriptConsole(PrintWriter writer) {
            this.writer = writer;
        }

        public void log(Object... args) {
            write(args);
        }

        public void info(Object... args) {
            write(args);
        }

        public void warn(Object... args) {
            write(args);
        }

        public void error(Object... args) {
            write(args);
        }

        private void write(Object... args) {
            if (writer == null) return;
            StringBuilder sb = new StringBuilder();
            if (args != null) {
                for (Object arg : args) {
                    sb.append(arg).append(" ");
                }
            }
            writer.println(sb);
            writer.flush();
        }
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        executor.shutdownNow();
    }
}
