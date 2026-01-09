package com.opencgl.dubbossl.controller;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.dubbo.common.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencgl.base.utils.DialogUtil;
import com.opencgl.base.utils.LoadingMask;
import com.opencgl.dubbossl.dao.DubboSslWidgetDao;
import com.opencgl.dubbossl.i18n.I18N;
import com.opencgl.dubbossl.model.DubboEnvConfig;
import com.opencgl.dubbossl.utils.DubboConfigFileParseUtil;
import com.opencgl.dubbossl.utils.ZkClientTestUtil;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXComboBox;
import io.github.palexdev.materialfx.controls.MFXTextField;
import io.github.palexdev.materialfx.theming.JavaFXThemes;
import io.github.palexdev.materialfx.theming.MaterialFXStylesheets;
import io.github.palexdev.materialfx.theming.UserAgentBuilder;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Dubbo SSL 环境配置对话框
 * 
 * @author Chance.W
 */
public class DubboSslEnvConfigureDialog {
    private static final Logger logger = LoggerFactory.getLogger(DubboSslEnvConfigureDialog.class);

    private static final String FXML_FILE = "/DubboSslEnvConfigureView.fxml";
    private final String basePath = com.opencgl.base.model.Base.BASE_PATH + "conf/dubbo/";

    @FXML protected StackPane envConfigRoot;
    @FXML protected MFXComboBox<String> envInfo;
    @FXML protected MFXTextField zkAddress;
    @FXML protected MFXTextField dubboGroup;
    @FXML protected MFXTextField apiPackagePath;
    @FXML protected MFXButton chooseJar;
    @FXML protected MFXButton cancelButton;
    @FXML protected MFXButton testEnv;
    @FXML protected MFXButton deleteButton;

    private Stage stage;
    private ExecutorService executor = newExecutor();
    private final Set<Future<?>> runningTasks = ConcurrentHashMap.newKeySet();
    private volatile boolean disposed;
    private final LoadingMask loadingMask = new LoadingMask();

    public void init() {
        UserAgentBuilder.builder()
            .themes(JavaFXThemes.MODENA)
            .themes(MaterialFXStylesheets.forAssemble(true))
            .setDeploy(true)
            .setResolveAssets(true)
            .build()
            .setGlobal();

        // 环境接口检测事件
        testEnv.setOnAction(actionEvent -> {
            if (StringUtils.isEmpty(String.valueOf(envInfo.getText())) ||
                StringUtils.isEmpty(zkAddress.getText()) ||
                StringUtils.isEmpty(dubboGroup.getText())) {
                DialogUtil.showErrorInfo(I18N.get("msg.zkGroupRequired"));
                return;
            }

            if (!zkAddress.getText().contains(".")) {
                DialogUtil.showErrorInfo(I18N.get("msg.zkAddressInvalid"));
                return;
            }
            
            String envName = envInfo.getText();
            String zkServer = zkAddress.getText();
            String registryGroup = dubboGroup.getText();
            String jarPathsStr = apiPackagePath.getText();
            String[] jarFile = jarPathsStr.split(",");
            StringBuilder message = new StringBuilder();
            
            submitAsync(() -> {
                runOnUi(() -> loadingMask.show(envConfigRoot));
                try {
                    DubboSslWidgetDao dubboSslWidgetDao = new DubboSslWidgetDao();
                    
                    // Construct config object
                    DubboEnvConfig config = DubboEnvConfig.builder()
                        .envName(envName)
                        .registryAddress(zkServer)
                        .registryGroup(registryGroup)
                        .apiPackagePath(jarPathsStr.replace("\\", "\\\\"))
                        .build();

                    List<String> totalServiceInfo = new java.util.ArrayList<>();

                    if (StringUtils.isEmpty(jarPathsStr) || !jarPathsStr.contains("jar")) {
                        config.setServiceData("[]");
                        
                        if (dubboSslWidgetDao.queryEnvConfig(envName) != null) {
                            dubboSslWidgetDao.updateEnvConfig(config);
                        } else {
                            dubboSslWidgetDao.insertEnvConfig(config);
                        }
                        
                        runOnUi(() -> DialogUtil.showSuccessInfo(I18N.get("msg.envConfigSuccess")));
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
                                // Add to total list
                                totalServiceInfo.addAll(serviceInformation);
                            }
                        }
                    }
                    
                    // Save to DB
                    config.setServiceData(com.alibaba.fastjson.JSON.toJSONString(totalServiceInfo));
                    if (dubboSslWidgetDao.queryEnvConfig(envName) != null) {
                        dubboSslWidgetDao.updateEnvConfig(config);
                    } else {
                        dubboSslWidgetDao.insertEnvConfig(config);
                    }
                    
                } catch (Throwable e) {
                    logger.error("", e);
                    runOnUi(() -> DialogUtil.showErrorInfo(e.getMessage()));
                    return;
                } finally {
                    runOnUi(() -> loadingMask.hide());
                }
                if (!StringUtils.isEmpty(message.toString())) {
                    runOnUi(() -> DialogUtil.showSuccessInfo(message.toString()));
                } else {
                    runOnUi(() -> DialogUtil.showSuccessInfo(I18N.get("msg.noRegistrySaved")));
                }
            });
        });

        cancelButton.setOnAction(actionEvent -> {
            Scene scene = envConfigRoot.getScene();
            Stage window = (Stage) scene.getWindow();
            window.close();
        });

        deleteButton.setOnAction(event -> {
            try {
                DubboSslWidgetDao dubboSslWidgetDao = new DubboSslWidgetDao();
                dubboSslWidgetDao.deleteEnvConfig(envInfo.getText());
                // Clear inputs
                envInfo.clear();
                zkAddress.clear();
                dubboGroup.clear();
                apiPackagePath.clear();
                envInfo.getItems().remove(envInfo.getText());
            } catch (Exception e) {
                logger.error("", e);
                DialogUtil.showErrorInfo(I18N.get("msg.deleteFailed", e.getMessage()));
            }
        });

        envInfo.setOnMouseClicked(event -> {
            try {
                DubboSslWidgetDao dubboSslWidgetDao = new DubboSslWidgetDao();
                List<DubboEnvConfig> configs = dubboSslWidgetDao.queryAllEnvConfig();
                List<String> names = configs.stream().map(DubboEnvConfig::getEnvName).collect(Collectors.toList());
                envInfo.setItems(FXCollections.observableArrayList(names));
            } catch (Exception e) {
                logger.error("", e);
            }
        });

        envInfo.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            // 先清空所有字段
            zkAddress.clear();
            dubboGroup.clear();
            apiPackagePath.clear();
            
            if (Objects.isNull(newValue) || newValue.isEmpty()) {
                return;
            }
            if (newValue.equals(oldValue)) {
                return;
            }
            
            try {
                DubboSslWidgetDao dubboSslWidgetDao = new DubboSslWidgetDao();
                DubboEnvConfig config = dubboSslWidgetDao.queryEnvConfig(newValue);
                if (config != null) {
                    logger.info("配置界面选择的客户端环境为 {}", config.getEnvName());
                    if (config.getRegistryAddress() != null) zkAddress.setText(config.getRegistryAddress());
                    if (config.getRegistryGroup() != null) dubboGroup.setText(config.getRegistryGroup());
                    if (config.getApiPackagePath() != null) apiPackagePath.setText(config.getApiPackagePath());
                }
            } catch (Exception e) {
                logger.error("", e);
            }
        });

        chooseJar.setOnAction(event -> {
            File alertfile;
            if (StringUtils.isEmpty(apiPackagePath.getText())) {
                alertfile = new File(basePath);
            } else {
                alertfile = new File(apiPackagePath.getText().split(",")[0]).getParentFile();
                if (alertfile == null || !alertfile.exists()) {
                    alertfile = new File(basePath);
                }
            }
            FileChooser jarfileChooser = new FileChooser();
            Stage fileChooseStage = new Stage();
            jarfileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Jar", "*.jar"));
            jarfileChooser.setTitle(I18N.get("filechooser.selectApiJar"));
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

    /**
     * 显示环境配置弹窗，相对 owner 居中（多屏/插件窗口时与当前操作窗口同屏）。
     * @param owner 所属窗口，可为 null
     */
    public void showAndWait(Window owner) throws IOException {
        if (stage == null) {
            stage = new Stage();
            stage.setOnHidden(event -> dispose());
            stage.initModality(Modality.APPLICATION_MODAL);
            if (owner != null) stage.initOwner(owner);

            FXMLLoader fxmlLoader = new FXMLLoader(this.getClass().getResource(FXML_FILE));
            fxmlLoader.setController(this);
            fxmlLoader.setResources(I18N.getBundle(I18N.getLocale()));
            Scene scene = new Scene(fxmlLoader.load());

            stage.setScene(scene);
            stage.setOnShown(e -> {
                if (owner != null) DialogUtil.centerStageOnOwner(stage, owner);
            });
            init();
        }
        stage.showAndWait();
    }

    private static ExecutorService newExecutor() { return Executors.newCachedThreadPool(r -> { Thread t = new Thread(r, "dubbo-ssl-env-worker"); t.setDaemon(true); return t; }); }
    private synchronized void submitAsync(Runnable action) {
        if (executor == null || executor.isShutdown()) executor = newExecutor();
        disposed = false;
        Future<?> task = executor.submit(() -> { if (!disposed) action.run(); });
        runningTasks.add(task);
    }
    private void runOnUi(Runnable action) { if (!disposed) Platform.runLater(() -> { if (!disposed) action.run(); }); }
    public void dispose() {
        if (disposed) return;
        disposed = true;
        for (Future<?> task : new java.util.ArrayList<>(runningTasks)) task.cancel(true);
        runningTasks.clear();
        if (executor != null) executor.shutdownNow();
    }
}
