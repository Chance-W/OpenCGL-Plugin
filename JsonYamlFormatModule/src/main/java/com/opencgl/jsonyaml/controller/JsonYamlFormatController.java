package com.opencgl.jsonyaml.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.opencgl.jsonyaml.i18n.I18N;
import com.opencgl.jsonyaml.views.JsonYamlFormatView;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.net.URL;
import java.util.ResourceBundle;

public class JsonYamlFormatController extends JsonYamlFormatView implements Initializable {

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initI18n();
        validateButton.setOnAction(e -> validateAction());
        formatButton.setOnAction(e -> formatAction());
        compressButton.setOnAction(e -> compressAction());
        clearButton.setOnAction(e -> clearAction());
    }

    private void initI18n() {
        if (titleLabel != null) titleLabel.textProperty().bind(I18N.getBinding("label.title"));
        if (inputLabel != null) inputLabel.textProperty().bind(I18N.getBinding("label.input"));
        outputLabel.textProperty().bind(I18N.getBinding("label.output"));
        validateButton.textProperty().bind(I18N.getBinding("button.validate"));
        formatButton.textProperty().bind(I18N.getBinding("button.format"));
        compressButton.textProperty().bind(I18N.getBinding("button.compress"));
        clearButton.textProperty().bind(I18N.getBinding("button.clear"));
        statusLabel.setText(I18N.get("label.ready"));
        jsonTab.setText(I18N.get("label.tab_json"));
        yamlTab.setText(I18N.get("label.tab_yaml"));
    }

    private boolean isJsonMode() {
        return typeTabPane.getSelectionModel().getSelectedIndex() == 0;
    }

    @FXML
    private void validateAction() {
        String text = inputArea.getText();
        if (text == null || text.isBlank()) {
            setStatus(I18N.get("msg.input_empty"));
            return;
        }
        if (isJsonMode()) {
            try {
                JSON.parse(text);
                outputArea.setText(I18N.get("msg.valid"));
                setStatus(I18N.get("msg.valid"));
            } catch (Exception e) {
                outputArea.setText(e.getMessage());
                setStatus(I18N.get("msg.invalid", e.getMessage()));
            }
        } else {
            try {
                new Yaml().load(text);
                outputArea.setText(I18N.get("msg.valid"));
                setStatus(I18N.get("msg.valid"));
            } catch (YAMLException e) {
                outputArea.setText(e.getMessage());
                setStatus(I18N.get("msg.invalid", e.getMessage()));
            }
        }
    }

    @FXML
    private void formatAction() {
        String text = inputArea.getText();
        if (text == null || text.isBlank()) {
            setStatus(I18N.get("msg.input_empty"));
            return;
        }
        try {
            if (isJsonMode()) {
                Object parsed = JSON.parse(text);
                String formatted = JSON.toJSONString(parsed, SerializerFeature.PrettyFormat, SerializerFeature.WriteDateUseDateFormat);
                outputArea.setText(formatted);
            } else {
                Yaml yaml = new Yaml();
                Object loaded = yaml.load(text);
                Yaml out = new Yaml();
                outputArea.setText(out.dump(loaded));
            }
            setStatus(I18N.get("msg.format_ok"));
        } catch (Exception e) {
            outputArea.setText(e.getMessage());
            setStatus(I18N.get("msg.format_fail"));
        }
    }

    @FXML
    private void compressAction() {
        String text = inputArea.getText();
        if (text == null || text.isBlank()) {
            setStatus(I18N.get("msg.input_empty"));
            return;
        }
        try {
            if (isJsonMode()) {
                Object parsed = JSON.parse(text);
                outputArea.setText(JSON.toJSONString(parsed));
            } else {
                Yaml yaml = new Yaml();
                Object loaded = yaml.load(text);
                Yaml out = new Yaml();
                outputArea.setText(out.dump(loaded).replace("\n", " ").replaceAll("\\s+", " ").trim());
            }
            setStatus(I18N.get("msg.compress_ok"));
        } catch (Exception e) {
            outputArea.setText(e.getMessage());
            setStatus(I18N.get("msg.format_fail"));
        }
    }

    @FXML
    private void clearAction() {
        inputArea.clear();
        outputArea.clear();
        setStatus(I18N.get("msg.cleared"));
    }

    private void setStatus(String message) {
        if (statusLabel != null) statusLabel.setText(message);
    }
}
