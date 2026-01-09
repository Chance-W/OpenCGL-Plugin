package com.opencgl.yaml.controller;

import com.opencgl.yaml.i18n.I18N;
import com.opencgl.yaml.service.YamlToolService;
import com.opencgl.yaml.service.YamlToolService.ValidationResult;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * YAML 工具控制器
 *
 * @author OpenCGL
 */
public class YamlToolController implements Initializable {

    @FXML
    private TextArea inputArea;
    @FXML
    private TextArea outputArea;
    @FXML
    private Label statusLabel;
    @FXML
    private Button formatBtn;
    @FXML
    private Button validateBtn;
    @FXML
    private Button yamlToJsonBtn;
    @FXML
    private Button jsonToYamlBtn;
    @FXML
    private Button yamlToPropsBtn;
    @FXML
    private Button propsToYamlBtn;
    @FXML
    private Button compressBtn;
    @FXML
    private Button swapBtn;
    @FXML
    private Button clearBtn;
    @FXML
    private Button copyToInputBtn;
    @FXML
    private Label inputLabel;
    @FXML
    private Label outputLabel;

    private final YamlToolService yamlService = new YamlToolService();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        inputArea.setText("""
                server:
                  port: 8080
                  host: localhost
                database:
                  url: jdbc:mysql://localhost:3306/db
                  username: root
                """);
        initI18n();
    }

    private void initI18n() {
        formatBtn.textProperty().bind(I18N.getBinding("button.format"));
        validateBtn.textProperty().bind(I18N.getBinding("button.validate"));
        yamlToJsonBtn.textProperty().bind(I18N.getBinding("button.yaml_to_json"));
        jsonToYamlBtn.textProperty().bind(I18N.getBinding("button.json_to_yaml"));
        yamlToPropsBtn.textProperty().bind(I18N.getBinding("button.yaml_to_props"));
        propsToYamlBtn.textProperty().bind(I18N.getBinding("button.props_to_yaml"));
        compressBtn.textProperty().bind(I18N.getBinding("button.compress"));
        swapBtn.textProperty().bind(I18N.getBinding("button.swap"));
        clearBtn.textProperty().bind(I18N.getBinding("button.clear"));
        copyToInputBtn.textProperty().bind(I18N.getBinding("button.copy_to_input"));
        inputLabel.textProperty().bind(I18N.getBinding("label.input"));
        outputLabel.textProperty().bind(I18N.getBinding("label.output"));
    }

    @FXML
    private void onFormat() {
        boolean inputSuccess = true;
        boolean outputSuccess = true;
        StringBuilder errorMsg = new StringBuilder();

        // 格式化输入区
        if (!inputArea.getText().isBlank()) {
            try {
                String input = inputArea.getText();
                String formatted = yamlService.format(input);
                inputArea.setText(formatted);
            } catch (Exception e) {
                inputSuccess = false;
                errorMsg.append(I18N.get("msg.input_error", e.getMessage())).append("; ");
            }
        }

        // 格式化输出区
        if (!outputArea.getText().isBlank()) {
            try {
                String output = outputArea.getText();
                String formatted = yamlService.format(output);
                outputArea.setText(formatted);
            } catch (Exception e) {
                outputSuccess = false;
                errorMsg.append(I18N.get("msg.output_error", e.getMessage()));
            }
        }

        if (inputSuccess && outputSuccess) {
            statusLabel.setText(I18N.get("msg.format_success"));
            statusLabel.setStyle("-fx-text-fill: #4caf50;");
        } else {
            statusLabel.setText("✗ " + errorMsg);
            statusLabel.setStyle("-fx-text-fill: #f44336;");
        }
    }

    @FXML
    private void onValidate() {
        String input = inputArea.getText();
        ValidationResult result = yamlService.validate(input);
        outputArea.setText(result.message());
        statusLabel.setText(result.valid() ? I18N.get("msg.validate_ok") : I18N.get("msg.validate_fail"));
        statusLabel.setStyle(result.valid() ? "-fx-text-fill: #4caf50;" : "-fx-text-fill: #f44336;");
    }

    @FXML
    private void onYamlToJson() {
        try {
            String input = inputArea.getText();
            String json = yamlService.yamlToJson(input);
            outputArea.setText(json);
            statusLabel.setText(I18N.get("msg.convert_success"));
            statusLabel.setStyle("-fx-text-fill: #4caf50;");
        } catch (Exception e) {
            outputArea.setText(I18N.get("msg.convert_failed", e.getMessage()));
            statusLabel.setText(I18N.get("msg.error"));
            statusLabel.setStyle("-fx-text-fill: #f44336;");
        }
    }

    @FXML
    private void onJsonToYaml() {
        try {
            String input = inputArea.getText();
            String yaml = yamlService.jsonToYaml(input);
            outputArea.setText(yaml);
            statusLabel.setText(I18N.get("msg.convert_success"));
            statusLabel.setStyle("-fx-text-fill: #4caf50;");
        } catch (Exception e) {
            outputArea.setText(I18N.get("msg.convert_failed", e.getMessage()));
            statusLabel.setText(I18N.get("msg.error"));
            statusLabel.setStyle("-fx-text-fill: #f44336;");
        }
    }

    @FXML
    private void onYamlToProperties() {
        try {
            String input = inputArea.getText();
            String properties = yamlService.yamlToProperties(input);
            outputArea.setText(properties);
            statusLabel.setText(I18N.get("msg.yaml_to_props_ok"));
            statusLabel.setStyle("-fx-text-fill: #4caf50;");
        } catch (Exception e) {
            outputArea.setText(I18N.get("msg.convert_failed", e.getMessage()));
            statusLabel.setText(I18N.get("msg.error"));
            statusLabel.setStyle("-fx-text-fill: #f44336;");
        }
    }

    @FXML
    private void onPropertiesToYaml() {
        try {
            String input = inputArea.getText();
            String yaml = yamlService.propertiesToYaml(input);
            outputArea.setText(yaml);
            statusLabel.setText(I18N.get("msg.props_to_yaml_ok"));
            statusLabel.setStyle("-fx-text-fill: #4caf50;");
        } catch (Exception e) {
            outputArea.setText(I18N.get("msg.convert_failed", e.getMessage()));
            statusLabel.setText(I18N.get("msg.error"));
            statusLabel.setStyle("-fx-text-fill: #f44336;");
        }
    }

    @FXML
    private void onCompress() {
        try {
            String input = inputArea.getText();
            String compressed = yamlService.compress(input);
            outputArea.setText(compressed);
            statusLabel.setText(I18N.get("msg.compress_ok"));
            statusLabel.setStyle("-fx-text-fill: #4caf50;");
        } catch (Exception e) {
            outputArea.setText(I18N.get("msg.compress_failed", e.getMessage()));
            statusLabel.setText(I18N.get("msg.error"));
            statusLabel.setStyle("-fx-text-fill: #f44336;");
        }
    }

    @FXML
    private void onCopyOutput() {
        String output = outputArea.getText();
        if (output != null && !output.isEmpty()) {
            inputArea.setText(output);
            statusLabel.setText(I18N.get("msg.copied_to_input"));
        }
    }

    @FXML
    private void onClear() {
        inputArea.clear();
        outputArea.clear();
        statusLabel.setText("");
    }

    @FXML
    private void onSwap() {
        String input = inputArea.getText();
        String output = outputArea.getText();
        inputArea.setText(output);
        outputArea.setText(input);
    }
}
