package com.opencgl;

import com.opencgl.template2.i18n.I18N;

import java.net.URL;

import com.opencgl.api.PluginUI;
import com.opencgl.api.ThemeAware;
import com.opencgl.api.ThemeInfo;
import io.github.palexdev.materialfx.controls.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * 示例插件2：演示主题切换实现类，包含丰富的 JavaFX/MaterialFX 组件
 */
public class Plugin2 implements PluginUI, ThemeAware {

    private VBox rootPane;
    private Label statusLabel;
    private MFXTextField textField;
    private MFXProgressBar progressBar;

    @Override
    public String directoryName() {
        return I18N.getOrDefault("label.category", "Plugin Template");
    }

    @Override
    public String name() {
        return I18N.getOrDefault("label.name", "Plugin Development Template 2");
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
        rootPane = new VBox(15);
        rootPane.setPadding(new Insets(20));
        rootPane.setAlignment(Pos.TOP_LEFT);

        statusLabel = new Label();
        statusLabel.textProperty().bind(I18N.getBinding("label.theme_default"));
        statusLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        textField = new MFXTextField();
        textField.floatingTextProperty().bind(I18N.getBinding("label.input_example"));
        textField.setPrefWidth(300);

        MFXButton primaryButton = new MFXButton();
        primaryButton.textProperty().bind(I18N.getBinding("label.primary_button"));
        primaryButton.getStyleClass().add("mfx-button-filled");

        MFXCheckbox checkbox = new MFXCheckbox();
        checkbox.textProperty().bind(I18N.getBinding("label.checkbox_example"));
        MFXToggleButton toggleButton = new MFXToggleButton();
        toggleButton.textProperty().bind(I18N.getBinding("label.toggle_example"));

        progressBar = new MFXProgressBar(0.6);
        progressBar.setPrefWidth(300);

        MFXSlider slider = new MFXSlider(0, 100, 50);
        slider.setPrefWidth(300);

        Label progressBarLabel = new Label();
        progressBarLabel.textProperty().bind(I18N.getBinding("label.progress_bar"));
        Label sliderLabel = new Label();
        sliderLabel.textProperty().bind(I18N.getBinding("label.slider"));

        rootPane.getChildren().addAll(
                statusLabel,
                new Separator(),
                textField,
                primaryButton,
                checkbox,
                toggleButton,
                progressBarLabel,
                progressBar,
                sliderLabel,
                slider);

        VBox.setVgrow(rootPane, Priority.ALWAYS);

        return rootPane;
    }

    @Override
    public void onThemeChanged(ThemeInfo theme) {
        if (rootPane == null)
            return;

        // 更新状态标签（使用 getOrDefault 避免 bundle 缺 key 时抛错）
        statusLabel.textProperty().unbind();
        String themeSuffix = theme.isDark()
                ? I18N.getOrDefault("label.theme_dark", "(Dark)")
                : I18N.getOrDefault("label.theme_light", "(Light)");
        statusLabel.setText(I18N.getOrDefault("label.status", "Current Theme: {0}", theme.getThemeName() + themeSuffix));

        // 动态更新样式
        String bgColor = theme.getBackgroundColor();
        String textColor = theme.getTextColor();
        String primaryColor = theme.getPrimaryColor();

        rootPane.setStyle("-fx-background-color: " + bgColor + ";");
        statusLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + textColor + ";");

        System.out.println(I18N.getOrDefault("msg.theme_changed", "[Plugin2] Theme changed: {0}", theme));
    }

    @Override
    public void dispose() {
        System.out.println(I18N.getOrDefault("msg.disposed", "[Plugin2] Resources cleared"));
    }
}
