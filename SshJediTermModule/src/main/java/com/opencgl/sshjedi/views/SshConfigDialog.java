package com.opencgl.sshjedi.views;

import com.opencgl.sshjedi.dao.SshConnectionDao;
import com.opencgl.sshjedi.model.SshConnectionDto;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXPasswordField;

import java.io.File;
import java.util.function.Consumer;

import com.opencgl.base.view.BaseCustomDialog;
import com.opencgl.sshjedi.i18n.I18N;

/**
 * 提取的公共 SSH 连接配置编辑弹窗
 */
public class SshConfigDialog {

    private static final SshConnectionDao dao = new SshConnectionDao();

    /**
     * @param originalData 原始连接信息（可为空）
     * @param onSaved      保存成功后的回调
     */
    public static void showDialog(SshConnectionDto originalData, Consumer<SshConnectionDto> onSaved) {
        BaseCustomDialog customDialog = new BaseCustomDialog();

        // 表单字段
        TextField nameField = new TextField();
        nameField.promptTextProperty().bind(I18N.getBinding("config.name.prompt"));
        TextField hostField = new TextField();
        TextField portField = new TextField();
        portField.setText("22");
        TextField usernameField = new TextField();
        MFXPasswordField passwordField = new MFXPasswordField();
        TextField privateKeyField = new TextField();

        // 填入当前数据
        if (originalData != null) {
            if (originalData.getName() != null)
                nameField.setText(originalData.getName());
            if (originalData.getHost() != null)
                hostField.setText(originalData.getHost());
            if (originalData.getPort() != null)
                portField.setText(String.valueOf(originalData.getPort()));
            if (originalData.getUsername() != null)
                usernameField.setText(originalData.getUsername());
            if (originalData.getPassword() != null)
                passwordField.setText(originalData.getPassword());
            if (originalData.getPrivateKeyPath() != null)
                privateKeyField.setText(originalData.getPrivateKeyPath());
        }

        // 构建表单布局
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(12);
        form.setPadding(new Insets(20));

        Label nameLabel = new Label();
        nameLabel.textProperty().bind(I18N.getBinding("config.name"));
        form.add(nameLabel, 0, 0);
        form.add(nameField, 1, 0);
        nameField.setPrefWidth(220);

        Label hostLabel = new Label();
        hostLabel.textProperty().bind(I18N.getBinding("config.host"));
        form.add(hostLabel, 0, 1);
        form.add(hostField, 1, 1);
        hostField.setPrefWidth(220);

        Label portLabel = new Label();
        portLabel.textProperty().bind(I18N.getBinding("config.port"));
        form.add(portLabel, 0, 2);
        form.add(portField, 1, 2);
        portField.setPrefWidth(80);

        Label userLabel = new Label();
        userLabel.textProperty().bind(I18N.getBinding("config.username"));
        form.add(userLabel, 0, 3);
        form.add(usernameField, 1, 3);

        Label pwdLabel = new Label();
        pwdLabel.textProperty().bind(I18N.getBinding("config.password"));
        form.add(pwdLabel, 0, 4);
        form.add(passwordField, 1, 4);

        Label keyLabel = new Label();
        keyLabel.textProperty().bind(I18N.getBinding("config.privateKey"));
        form.add(keyLabel, 0, 5);

        HBox keyRow = new HBox(6);
        keyRow.setAlignment(Pos.CENTER_LEFT);
        privateKeyField.setPrefWidth(180);
        MFXButton browseBtn = new MFXButton();
        browseBtn.textProperty().bind(I18N.getBinding("config.btn.browse"));
        browseBtn.getStyleClass().add("secondary-button");
        browseBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.titleProperty().bind(I18N.getBinding("config.dialog.chooseKey"));
            try {
                File sshDir = new File(System.getProperty("user.home") + "/.ssh");
                if (sshDir.exists())
                    fc.setInitialDirectory(sshDir);
            } catch (Exception ignored) {
            }
            File selected = fc.showOpenDialog(customDialog);
            if (selected != null)
                privateKeyField.setText(selected.getAbsolutePath());
        });
        keyRow.getChildren().addAll(privateKeyField, browseBtn);
        form.add(keyRow, 1, 5);

        // 按钮行
        HBox btnRow = new HBox(10);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        btnRow.setPadding(new Insets(0, 20, 16, 20));

        MFXButton cancelBtn = new MFXButton();
        cancelBtn.textProperty().bind(I18N.getBinding("config.btn.cancel"));
        cancelBtn.getStyleClass().add("secondary-button");
        MFXButton saveBtn = new MFXButton();
        saveBtn.textProperty().bind(I18N.getBinding("config.btn.save"));
        saveBtn.getStyleClass().add("primary-button");

        btnRow.getChildren().addAll(cancelBtn, saveBtn);

        VBox dialogRoot = new VBox(form, btnRow);

        customDialog.dialogTitleProperty().bind(
                originalData != null ? I18N.getBinding("config.title.edit") : I18N.getBinding("config.title.new"));
        customDialog.setContent(dialogRoot);

        cancelBtn.setOnAction(e -> customDialog.close());
        saveBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            String host = hostField.getText().trim();
            String user = usernameField.getText().trim();
            
            boolean isLocal = host.startsWith("LOCAL:");
            // 如果是本地终端，只需校验名称；如果是 SSH，则校验名称、主机和用户
            if (name.isEmpty() || (!isLocal && (host.isEmpty() || user.isEmpty()))) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setHeaderText(null);
                alert.setContentText(I18N.get("config.msg.empty"));
                alert.showAndWait();
                return;
            }

            SshConnectionDto dataToSave = originalData != null ? originalData : new SshConnectionDto();
            dataToSave.setHost(host);
            try {
                dataToSave.setPort(Integer.parseInt(portField.getText().trim()));
            } catch (NumberFormatException ex) {
                dataToSave.setPort(22);
            }
            dataToSave.setUsername(user);
            dataToSave.setPassword(passwordField.getText());
            dataToSave.setPrivateKeyPath(privateKeyField.getText().trim());

            // 设置名称
            dataToSave.setName(name);

            // 只要编辑了连接属性，就强制将其转为连接型实体(叶子节点)
            dataToSave.setIsLeaf(true);

            try {
                if (dataToSave.getId() != null) {
                    dao.update(dataToSave);
                } else {
                    dao.insert(dataToSave);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            if (onSaved != null) {
                onSaved.accept(dataToSave);
            }
            customDialog.close();
        });

        customDialog.showAndWait();
    }
}
