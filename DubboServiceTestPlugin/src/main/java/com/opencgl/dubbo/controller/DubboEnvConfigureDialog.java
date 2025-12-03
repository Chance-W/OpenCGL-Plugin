package com.opencgl.dubbo.controller;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang.StringUtils;
import org.apache.dubbo.common.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencgl.base.model.Base;
import com.opencgl.base.utils.DialogUtil;
import com.opencgl.base.utils.LoadingUtil;
import com.opencgl.dubbo.utils.DubboConfigFileParseUtil;
import com.opencgl.dubbo.utils.ZkClientTestUtil;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXComboBox;
import io.github.palexdev.materialfx.controls.MFXTextField;
import io.github.palexdev.materialfx.theming.JavaFXThemes;
import io.github.palexdev.materialfx.theming.MaterialFXStylesheets;
import io.github.palexdev.materialfx.theming.UserAgentBuilder;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * @author Chance.W
 */
public class DubboEnvConfigureDialog {
    private static final Logger logger = LoggerFactory.getLogger(DubboEnvConfigureDialog.class);

    private static final String FXML_FILE = "/DubboEnvConfigureView.fxml";

    @FXML
    protected StackPane envConfigRoot;
    @FXML
    protected MFXComboBox<String> envInfo;
    @FXML
    protected MFXTextField zkAddress;
    @FXML
    protected MFXTextField dubboGroup;
    @FXML
    protected MFXTextField apiPackagePath;
    @FXML
    protected MFXButton chooseJar;
    @FXML
    protected MFXButton cancelButton;
    @FXML
    protected MFXButton testEnv;
    @FXML
    protected MFXButton deleteButton;

    private Stage stage;

    public void init() {
       // MFXThemeManager.addOn(envConfigRoot, Themes.DEFAULT, Themes.LEGACY);
        UserAgentBuilder.builder()
            .themes(JavaFXThemes.MODENA)
            .themes(MaterialFXStylesheets.forAssemble(true))
            .setDeploy(true)
            .setResolveAssets(true)
            .build()
            .setGlobal();


        //环境接口检测事件
        testEnv.setOnAction(actionEvent -> {
            if (StringUtils.isEmpty(String.valueOf(envInfo.getText())) ||
                StringUtils.isEmpty(zkAddress.getText()) ||
                StringUtils.isEmpty(dubboGroup.getText())) {
                DialogUtil.showErrorInfo("zk和分组不能为空", stage);
                return;
            }

            if (!zkAddress.getText().contains(".")) {
                DialogUtil.showErrorInfo("zk地址配置不合法", stage);
                return;
            }
            String configPathName = Base.BASE_PATH + "/conf/dubbo/" + envInfo.getText();
            String zkServer = zkAddress.getText();
            String registryGroup = dubboGroup.getText();
            String[] jarFile = apiPackagePath.getText().split(",");
            StringBuilder message = new StringBuilder();
            CompletableFuture.runAsync(() -> {
                Platform.runLater(() -> LoadingUtil.show(envConfigRoot));
                try {
                    //删除对应记录
                    DubboConfigFileParseUtil.initializeFile(configPathName);
                    logger.info("开始写入配置到文件" + configPathName);
                    DubboConfigFileParseUtil.operClientParmeterFile(configPathName, "registryGroup", dubboGroup.getText());
                    DubboConfigFileParseUtil.operClientParmeterFile(configPathName, "registryAddress", zkAddress.getText());
                    DubboConfigFileParseUtil.operClientParmeterFile(configPathName, "projectDubboAddress", apiPackagePath.getText().replace("\\", "\\\\"));
                    if (StringUtils.isEmpty(apiPackagePath.getText()) || !apiPackagePath.getText().contains("jar")) {
                        Platform.runLater(() -> DialogUtil.showSuccessInfo("环境信息配置成功", stage));
                        return;
                    }


                    List<String> interfaces = DubboConfigFileParseUtil.listInterface(jarFile, DubboConfigFileParseUtil.getInformationFromJar(jarFile));

                    ZkClientTestUtil zkClientTest = new ZkClientTestUtil();
                    List<URL> providers = zkClientTest.getNewProvider(zkServer, registryGroup, interfaces, 20000);
                    for (URL url : providers) {
                        logger.info("注册成功的接口为" + url);
                        for (int j = 0; j < interfaces.size(); j++) {
                            if (url.toString().contains(interfaces.get(j).replace("interface ", ""))) {
                                message.append(interfaces.get(j).replace("interface", ""));
                                String service = interfaces.get(j).replace("interface ", "");
                                List<String> serviceInformation = DubboConfigFileParseUtil.getMethodAndParamer(jarFile, service);
                                for (int k = 0; k < serviceInformation.size(); k++) {
                                    DubboConfigFileParseUtil.operClientParmeterFile(configPathName, "service[" + j + "]" + "[" + k + "]", serviceInformation.get(k));
                                }
                            }
                        }
                    }
                }
                catch (Throwable e) {
                    logger.error("", e);
                    Platform.runLater(() -> DialogUtil.showErrorInfo(e.getMessage(), stage));
                    return;
                }
                finally {
                    Platform.runLater(() -> LoadingUtil.remove(envConfigRoot));
                }
                if (!StringUtils.isEmpty(message.toString())) {
                    Platform.runLater(() -> DialogUtil.showSuccessInfo(message.toString(), stage));
                }
                else {
                    Platform.runLater(() -> DialogUtil.showSuccessInfo("无注册信息,环境信息已保存", stage));
                }

            });
        });

        cancelButton.setOnAction(actionEvent -> {
            Scene scene = envConfigRoot.getScene();
            Stage window = (Stage) scene.getWindow();
            window.close();
        });

        deleteButton.setOnAction(event -> {
            String configPathName = Base.BASE_PATH + "/conf/dubbo/" + envInfo.getText();
            DubboConfigFileParseUtil.deleteFile(configPathName);
        });


        envInfo.setOnMouseClicked(event -> envInfo.setItems(DubboConfigFileParseUtil.getFileList(Base.BASE_PATH + "/conf/dubbo/")));


        envInfo.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (Objects.isNull(newValue)) {
                zkAddress.clear();
                dubboGroup.clear();
                apiPackagePath.clear();
                return;
            }
            if (newValue.equals(oldValue)) {
                return;
            }
            String configPathName = Base.BASE_PATH + "/conf/dubbo/" + envInfo.getText();
            for (Object fileName : DubboConfigFileParseUtil.getFileList(Base.BASE_PATH + "/conf/dubbo/")) {
                if (fileName.toString().equals(envInfo.getText())) {
                    logger.info("配置界面选择的客户端文件为" + envInfo.getText());
                    zkAddress.setText(DubboConfigFileParseUtil.readClientParameter(configPathName, "registryAddress"));
                    dubboGroup.setText(DubboConfigFileParseUtil.readClientParameter(configPathName, "registryGroup"));
                    apiPackagePath.setText(DubboConfigFileParseUtil.readClientParameter(configPathName, "projectDubboAddress"));
                }
            }
        });


        chooseJar.setOnAction(event -> {
            File alertfile;
            if (StringUtils.isEmpty(apiPackagePath.getText())) {
                alertfile = new File(Base.BASE_PATH);
            }
            else {
                alertfile = new File(apiPackagePath.getText().split(",")[0]).getParentFile();
                if (!alertfile.getParentFile().exists()) {
                    alertfile = new File(Base.BASE_PATH);
                }
            }
            FileChooser jarfileChooser = new FileChooser();
            Stage fileChooseStage = new Stage();
            jarfileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Jar", "*.jar"));
            jarfileChooser.setTitle("请选择需要匹配接口的api包");
            jarfileChooser.setInitialDirectory(alertfile);
            List<File> listFile = jarfileChooser.showOpenMultipleDialog(fileChooseStage);
            if (null == listFile) {
                return;
            }
            StringBuilder path = new StringBuilder();
            for (File value : listFile) {
                File file = new File(value.toString());
                if (file.exists()) {
                    path.append(value).append(",");
                }
            }
            if (!"".contentEquals(path)) {
                apiPackagePath.setText(path.toString());
            }
        });
    }

    public void showAndWait() throws IOException {
        if (stage == null) {
            stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);

            FXMLLoader fxmlLoader = new FXMLLoader(this.getClass().getResource(FXML_FILE));
            fxmlLoader.setController(this);
            Scene scene = new Scene(fxmlLoader.load());

            stage.setScene(scene);
            init();
        }
        stage.showAndWait();
    }
}
