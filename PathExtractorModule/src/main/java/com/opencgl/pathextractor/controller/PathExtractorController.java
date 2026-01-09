package com.opencgl.pathextractor.controller;

import com.opencgl.pathextractor.i18n.I18N;
import com.opencgl.pathextractor.service.JsonPathService;
import com.opencgl.pathextractor.service.XPathService;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import org.fxmisc.richtext.CodeArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.ResourceBundle;

public class PathExtractorController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(PathExtractorController.class);
    
    public BorderPane rootPane;
    public ToggleGroup typeGroup;
    public RadioButton jsonRadio;
    public RadioButton xmlRadio;
    public Button openFileButton;
    public Button clearButton;
    
    public CodeArea inputArea;
    public TextField pathField;
    public Button extractButton;
    public Button example1Button;
    public Button example2Button;
    public Button example3Button;
    
    public Label statusLabel;
    public Label inputDataLabel;
    public Label pathLabel;
    public Label exampleLabel;
    public Label resultLabel;
    public CodeArea resultArea;
    public Button copyButton;
    public Button formatButton;
    
    private JsonPathService jsonPathService;
    private XPathService xPathService;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        jsonPathService = new JsonPathService();
        xPathService = new XPathService();
        
        setupUI();
        bindEvents();
        initI18n();
        
        // 默认示例
        loadJsonExample();
    }

    private void initI18n() {
        if (jsonRadio != null) jsonRadio.textProperty().bind(I18N.getBinding("label.json"));
        if (xmlRadio != null) xmlRadio.textProperty().bind(I18N.getBinding("label.xml"));
        if (openFileButton != null) openFileButton.textProperty().bind(I18N.getBinding("btn.openFile"));
        if (clearButton != null) clearButton.textProperty().bind(I18N.getBinding("btn.clear"));
        if (inputDataLabel != null) inputDataLabel.textProperty().bind(I18N.getBinding("label.inputData"));
        if (pathLabel != null) pathLabel.textProperty().bind(I18N.getBinding("label.pathExpression"));
        if (extractButton != null) extractButton.textProperty().bind(I18N.getBinding("btn.extract"));
        if (exampleLabel != null) exampleLabel.textProperty().bind(I18N.getBinding("label.example"));
        if (resultLabel != null) resultLabel.textProperty().bind(I18N.getBinding("label.result"));
        if (copyButton != null) copyButton.textProperty().bind(I18N.getBinding("btn.copyResult"));
        if (formatButton != null) formatButton.textProperty().bind(I18N.getBinding("btn.formatInput"));
    }

    private void setupUI() {
        inputArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px;");
        resultArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px;");
    }

    private void bindEvents() {
        // 类型切换
        jsonRadio.selectedProperty().addListener((obs, old, val) -> {
            if (val) {
                pathField.promptTextProperty().unbind();
                pathField.setPromptText(I18N.get("prompt.pathJson"));
                loadJsonExample();
            }
        });
        
        xmlRadio.selectedProperty().addListener((obs, old, val) -> {
            if (val) {
                pathField.promptTextProperty().unbind();
                pathField.setPromptText(I18N.get("prompt.pathXml"));
                loadXmlExample();
            }
        });
        
        // 按钮事件
        openFileButton.setOnAction(e -> openFile());
        clearButton.setOnAction(e -> clearAll());
        extractButton.setOnAction(e -> extract());
        copyButton.setOnAction(e -> copyResult());
        formatButton.setOnAction(e -> formatInput());
        
        // 示例按钮
        example1Button.setOnAction(e -> pathField.setText("$.store.book[0]"));
        example2Button.setOnAction(e -> pathField.setText("$.store.book[*].title"));
        example3Button.setOnAction(e -> pathField.setText("//book[@id='1']/title"));
        
        // 回车提取
        pathField.setOnAction(e -> extract());
    }

    private void extract() {
        String input = inputArea.getText();
        String path = pathField.getText();
        
        if (input.trim().isEmpty()) {
            statusLabel.setText(I18N.get("status.pleaseInputData"));
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }
        
        if (path.trim().isEmpty()) {
            statusLabel.setText(I18N.get("status.pleaseInputPath"));
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }
        
        String result;
        if (jsonRadio.isSelected()) {
            result = jsonPathService.extract(input, path);
        } else {
            result = xPathService.extract(input, path);
        }
        
        resultArea.replaceText(result);
        
        if (result.startsWith("错误") || result.startsWith("Error")) {
            statusLabel.setText(I18N.get("status.extractFailed"));
            statusLabel.setStyle("-fx-text-fill: red;");
        } else {
            statusLabel.setText(I18N.get("status.extractSuccess"));
            statusLabel.setStyle("-fx-text-fill: green;");
        }
    }

    private void openFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18N.get("filechooser.openFile"));
        if (jsonRadio.isSelected()) {
            chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(I18N.get("filter.json"), "*.json"));
        } else {
            chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(I18N.get("filter.xml"), "*.xml"));
        }
        
        File file = chooser.showOpenDialog(rootPane.getScene().getWindow());
        if (file != null) {
            try {
                String content = Files.readString(file.toPath());
                inputArea.replaceText(content);
                statusLabel.setText(I18N.get("status.loaded", file.getName()));
                statusLabel.setStyle("-fx-text-fill: green;");
            } catch (Exception e) {
                logger.error("读取文件失败", e);
                statusLabel.setText(I18N.get("status.readFailed"));
                statusLabel.setStyle("-fx-text-fill: red;");
            }
        }
    }

    private void clearAll() {
        inputArea.clear();
        pathField.clear();
        resultArea.clear();
        statusLabel.setText(I18N.get("status.cleared"));
        statusLabel.setStyle("-fx-text-fill: gray;");
    }

    private void copyResult() {
        String result = resultArea.getText();
        if (!result.isEmpty()) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(result);
            clipboard.setContent(content);
            
            statusLabel.setText(I18N.get("status.copied"));
            statusLabel.setStyle("-fx-text-fill: green;");
        }
    }

    private void formatInput() {
        String input = inputArea.getText();
        if (input.trim().isEmpty()) {
            return;
        }
        
        try {
            if (jsonRadio.isSelected()) {
                String formatted = com.alibaba.fastjson2.JSON.toJSONString(
                    com.alibaba.fastjson2.JSON.parse(input),
                    com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat
                );
                inputArea.replaceText(formatted);
                statusLabel.setText(I18N.get("status.formatted"));
                statusLabel.setStyle("-fx-text-fill: green;");
            }
        } catch (Exception e) {
            statusLabel.setText(I18N.get("status.formatFailed"));
            statusLabel.setStyle("-fx-text-fill: red;");
        }
    }

    private void loadJsonExample() {
        String example = "{\n" +
            "  \"store\": {\n" +
            "    \"book\": [\n" +
            "      {\n" +
            "        \"category\": \"fiction\",\n" +
            "        \"title\": \"The Lord of the Rings\",\n" +
            "        \"price\": 22.99\n" +
            "      },\n" +
            "      {\n" +
            "        \"category\": \"fiction\",\n" +
            "        \"title\": \"Harry Potter\",\n" +
            "        \"price\": 8.99\n" +
            "      }\n" +
            "    ]\n" +
            "  }\n" +
            "}";
        inputArea.replaceText(example);
        pathField.setText("$.store.book[*].title");
    }

    private void loadXmlExample() {
        String example = "<?xml version=\"1.0\"?>\n" +
            "<catalog>\n" +
            "  <book id=\"1\">\n" +
            "    <title>The Lord of the Rings</title>\n" +
            "    <price>22.99</price>\n" +
            "  </book>\n" +
            "  <book id=\"2\">\n" +
            "    <title>Harry Potter</title>\n" +
            "    <price>8.99</price>\n" +
            "  </book>\n" +
            "</catalog>";
        inputArea.replaceText(example);
        pathField.setText("//book/title");
    }
}
