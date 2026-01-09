package com.opencgl;

import java.net.URL;
import java.util.Locale;
import java.util.ResourceBundle;

import com.opencgl.api.PluginI18n;
import com.opencgl.api.PluginUI;
import io.github.palexdev.materialfx.controls.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * 示例插件3：演示语言实现类切换，采用不依赖 OpenCGL-Base 的纯净实现方式
 */
public class Plugin3 implements PluginUI, PluginI18n {

    // 界面绑定的属性
    private final StringProperty pluginName = new SimpleStringProperty();
    private final StringProperty pluginCategory = new SimpleStringProperty();
    private final StringProperty statusText = new SimpleStringProperty();
    private final StringProperty inputLabelText = new SimpleStringProperty();
    private final StringProperty promptText = new SimpleStringProperty();
    private final StringProperty btnText = new SimpleStringProperty();
    private final StringProperty checkboxText = new SimpleStringProperty();
    private final StringProperty toggleText = new SimpleStringProperty();
    private final StringProperty progressLabelText = new SimpleStringProperty();
    private final StringProperty sliderLabelText = new SimpleStringProperty();

    public Plugin3() {
        // 初始加载
        refreshI18n(Locale.getDefault());
    }

    private void refreshI18n(Locale locale) {
        System.out.println("[Plugin3] 正在加载语言资源, Locale: " + locale);
        ResourceBundle bundle;
        try {
            // 使用 com.opencgl.Template 作为基准名
            bundle = ResourceBundle.getBundle("com.opencgl.Template", locale, this.getClass().getClassLoader());
            System.out.println("[Plugin3] 成功加载资源包: " + bundle.getLocale());
        }
        catch (Exception e) {
            System.err.println("[Plugin3] 加载资源包失败: " + e.getMessage() + ", 回退到中文");
            bundle = ResourceBundle.getBundle("com.opencgl.Template", Locale.CHINA, this.getClass().getClassLoader());
        }

        // 更新绑定属性，从而触发 UI 自动刷新
        pluginName.set(bundle.getString("plugin.name"));
        pluginCategory.set(bundle.getString("plugin.category"));
        statusText.set(bundle.getString("label.status"));
        inputLabelText.set(bundle.getString("label.input"));
        promptText.set(bundle.getString("prompt.text"));
        btnText.set(bundle.getString("btn.primary"));
        checkboxText.set(bundle.getString("checkbox.sample"));
        toggleText.set(bundle.getString("toggle.sample"));
        progressLabelText.set(bundle.getString("label.progress"));
        sliderLabelText.set(bundle.getString("label.slider"));
    }

    @Override
    public String directoryName() {
        return pluginCategory.get();
    }

    @Override
    public String name() {
        return pluginName.get();
    }

    @Override
    public URL iconPath() {
        return null;
    }

    @Override
    public UIType type() {
        return UIType.JAVAFX;
    }

    @Override
    public Object createView() {
        VBox rootPane = new VBox(15);
        rootPane.setPadding(new Insets(20));
        rootPane.setAlignment(Pos.TOP_LEFT);

        Label statusLabel = new Label();
        statusLabel.textProperty().bind(statusText);
        statusLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label inputLabel = new Label();
        inputLabel.textProperty().bind(inputLabelText);

        MFXTextField textField = new MFXTextField();
        textField.floatingTextProperty().bind(promptText);
        textField.setPrefWidth(300);

        MFXButton primaryButton = new MFXButton();
        primaryButton.textProperty().bind(btnText);
        primaryButton.getStyleClass().add("mfx-button-filled");

        MFXCheckbox checkbox = new MFXCheckbox();
        checkbox.textProperty().bind(checkboxText);

        MFXToggleButton toggleButton = new MFXToggleButton();
        toggleButton.textProperty().bind(toggleText);

        Label progressLabel = new Label();
        progressLabel.textProperty().bind(progressLabelText);

        MFXProgressBar progressBar = new MFXProgressBar(0.3);
        progressBar.setPrefWidth(300);

        Label sliderLabel = new Label();
        sliderLabel.textProperty().bind(sliderLabelText);

        MFXSlider slider = new MFXSlider(0, 100, 20);
        slider.setPrefWidth(300);

        rootPane.getChildren().addAll(
            statusLabel,
            new Separator(),
            inputLabel,
            textField,
            primaryButton,
            checkbox,
            toggleButton,
            progressLabel,
            progressBar,
            sliderLabel,
            slider);

        VBox.setVgrow(rootPane, Priority.ALWAYS);
        return rootPane;
    }

    @Override
    public void onLanguageChange(Locale newLocale) {
        System.out.println("[Plugin3] 收到语言切换通知，手动加载资源: " + newLocale);
        refreshI18n(newLocale);
    }

    @Override
    public void dispose() {
        System.out.println("[Plugin3] 资源已清理");
    }
}
