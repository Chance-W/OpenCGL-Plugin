package com.opencgl.dubbo.controller;

import java.io.File;
import java.io.IOException;
import java.util.List;
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

import com.alibaba.fastjson.JSON;
import com.opencgl.base.model.Base;
import com.opencgl.base.theme.ThemeManager;
import com.opencgl.base.utils.DialogUtil;
import com.opencgl.base.utils.LoadingUtil;
import com.opencgl.base.utils.TooltipUtil;
import com.opencgl.dubbo.dao.DubboWidgetDao;
import com.opencgl.dubbo.i18n.I18N;
import com.opencgl.dubbo.model.DubboEnvConfig;
import com.opencgl.dubbo.utils.DubboConfigFileParseUtil;
import com.opencgl.dubbo.utils.ZkClientTestUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * @author Chance.W
 */
public class DubboEnvConfigureDialog {
    private static final Logger logger = LoggerFactory.getLogger(DubboEnvConfigureDialog.class);

    private static final String FXML_FILE = "/DubboEnvConfigureView.fxml";

    @FXML
    protected StackPane envConfigRoot;
    @FXML
    protected ListView<String> envListView;
    @FXML
    protected Label envListLabel;
    @FXML
    protected Label envConfigLabel;
    @FXML
    protected Label envNameLabel;
    @FXML
    protected Label zkAddressLabel;
    @FXML
    protected Label zkGroupLabel;
    @FXML
    protected Label apiPathLabel;
    @FXML
    protected Tooltip chooseJarTooltip;
    @FXML
    protected Tooltip scanTooltip;

    @FXML
    protected TextField envNameField;
    @FXML
    protected TextField zkAddressField;
    @FXML
    protected TextField groupField;
    @FXML
    protected TextField packageField;

    @FXML
    protected Button newButton;
    @FXML
    protected Button deleteButton;
    @FXML
    protected Button chooseJarButton;
    @FXML
    protected Button scanButton;
    @FXML
    protected Button cancelButton;
    @FXML
    protected Button saveButton;

    private static final I18N i18n = new I18N();

    private Stage stage;
    private ExecutorService executor = newExecutor();
    private final Set<Future<?>> runningTasks = ConcurrentHashMap.newKeySet();
    private volatile boolean disposed;

    public void init() {
        // Multi-language bindings
        envListLabel.textProperty().bind(I18N.getBinding("dubbo.env.dialog.list.title"));
        envConfigLabel.textProperty().bind(I18N.getBinding("dubbo.env.dialog.form.title"));
        envNameLabel.textProperty().bind(I18N.getBinding("dubbo.env.dialog.label.name"));
        envNameField.promptTextProperty().bind(I18N.getBinding("dubbo.env.dialog.prompt.name"));
        zkAddressLabel.textProperty().bind(I18N.getBinding("dubbo.env.dialog.label.zk_address"));
        zkAddressField.promptTextProperty()
                .bind(I18N.getBinding("dubbo.env.dialog.prompt.zk_address"));
        zkGroupLabel.textProperty().bind(I18N.getBinding("dubbo.env.dialog.label.zk_group"));
        groupField.promptTextProperty()
                .bind(I18N.getBinding("dubbo.env.dialog.prompt.zk_group"));
        apiPathLabel.textProperty().bind(I18N.getBinding("dubbo.env.dialog.label.api_path"));
        packageField.promptTextProperty()
                .bind(I18N.getBinding("dubbo.env.dialog.prompt.api_path"));

        newButton.textProperty().bind(I18N.getBinding("dubbo.env.dialog.button.new"));
        deleteButton.textProperty().bind(I18N.getBinding("dubbo.env.dialog.button.delete"));
        scanButton.textProperty().bind(I18N.getBinding("dubbo.env.dialog.button.scan"));
        saveButton.textProperty().bind(I18N.getBinding("dubbo.env.dialog.button.save"));
        cancelButton.textProperty().bind(I18N.getBinding("dubbo.env.dialog.button.close"));

        chooseJarTooltip.textProperty().bind(I18N.getBinding("dubbo.env.dialog.tooltip.choose"));
        scanTooltip.textProperty().bind(I18N.getBinding("dubbo.env.dialog.tooltip.scan"));

        // Load Environments
        refreshEnvList();

        // Listener for selection
        envListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadEnvDetails(newVal);
            }
        });

        // New Button
        newButton.setOnAction(e -> {
            envListView.getSelectionModel().clearSelection();
            clearForm();
            envNameField.requestFocus();
        });

        // Save/Test Button
        saveButton.setOnAction(actionEvent -> {
            if (StringUtils.isEmpty(envNameField.getText()) ||
                    StringUtils.isEmpty(zkAddressField.getText()) ||
                    StringUtils.isEmpty(groupField.getText())) {
                DialogUtil.showErrorInfo(I18N.get("dubbo.env.dialog.error.empty"), stage);
                return;
            }

            if (!zkAddressField.getText().contains(".")) {
                DialogUtil.showErrorInfo(I18N.get("dubbo.env.dialog.error.invalid_zk"), stage);
                return;
            }

            String envName = envNameField.getText();
            String zkServer = zkAddressField.getText();
            String zkGroup = groupField.getText();
            String jarPathsStr = packageField.getText();

            submitAsync(() -> {
                runOnUi(() -> LoadingUtil.show(envConfigRoot));
                try {
                    // Construct config object
                    DubboEnvConfig config = DubboEnvConfig.builder()
                            .envName(envName)
                            .registryAddress(zkServer)
                            .zkGroup(zkGroup)
                            .registryGroup(zkGroup) // 同步保留兼容字段
                            .apiPackagePath(jarPathsStr == null ? "" : jarPathsStr.replace("\\", "\\\\"))
                            .build();

                    List<String> totalServiceInfo = new java.util.ArrayList<>();
                    StringBuilder message = new StringBuilder();

                    if (StringUtils.isEmpty(jarPathsStr) || !jarPathsStr.contains("jar")) {
                        config.setServiceData("[]");
                        saveToDb(config);
                        runOnUi(() -> {
                            TooltipUtil.showToast(envConfigRoot,
                                    I18N.get("dubbo.env.dialog.success.no_scan"));
                            refreshEnvList();
                        });
                        return;
                    }

                    // Scan Logic
                    String[] jarFile = jarPathsStr.split(",");
                    List<String> interfaces = DubboConfigFileParseUtil.listInterface(jarFile,
                            DubboConfigFileParseUtil.getInformationFromJar(jarFile));

                    ZkClientTestUtil zkClientTest = new ZkClientTestUtil();
                    List<URL> providers = zkClientTest.getNewProvider(zkServer, zkGroup, interfaces, 20000);

                    for (URL url : providers) {
                        logger.info("注册成功的接口为 {}", url);
                        for (String anInterface : interfaces) {
                            if (url.toString().contains(anInterface.replace("interface ", ""))) {
                                message.append(anInterface.replace("interface", ""));
                                String service = anInterface.replace("interface ", "");
                                List<String> serviceInformation = DubboConfigFileParseUtil.getMethodAndParamer(jarFile,
                                        service);
                                totalServiceInfo.addAll(serviceInformation);
                            }
                        }
                    }

                    config.setServiceData(JSON.toJSONString(totalServiceInfo));
                    saveToDb(config);

                    runOnUi(() -> {
                        if (!message.isEmpty()) {
                            TooltipUtil.showToast(envConfigRoot, com.opencgl.dubbo.i18n.I18N
                                    .get("dubbo.env.dialog.success.scan_found", message.toString()));
                        } else {
                            TooltipUtil.showToast(envConfigRoot,
                                    I18N.get("dubbo.env.dialog.success.no_provider"));
                        }
                        refreshEnvList();
                    });

                } catch (Throwable e) {
                    logger.error("", e);
                    runOnUi(() -> DialogUtil.showErrorInfo(e.getMessage(), stage));
                } finally {
                    runOnUi(() -> LoadingUtil.remove(envConfigRoot));
                }
            });
        });

        cancelButton.setOnAction(actionEvent -> stage.close());

        deleteButton.setOnAction(event ->

        {
            String selected = envListView.getSelectionModel().getSelectedItem();
            if (selected == null)
                return;
            try {
                DubboWidgetDao dubboWidgetDao = new DubboWidgetDao();
                dubboWidgetDao.deleteEnvConfig(selected);
                clearForm();
                refreshEnvList();
            } catch (Exception e) {
                logger.error("", e);
                DialogUtil.showErrorInfo(
                        I18N.get("dubbo.env.dialog.error.delete_failed", e.getMessage()), stage);
            }
        });

        chooseJarButton.setOnAction(event -> {
            File alertfile;
            if (StringUtils.isEmpty(packageField.getText())) {
                alertfile = new File(Base.BASE_PATH);
            } else {
                alertfile = new File(packageField.getText().split(",")[0]).getParentFile();
                if (alertfile == null || !alertfile.exists()) {
                    alertfile = new File(Base.BASE_PATH);
                }
            }
            FileChooser jarfileChooser = new FileChooser();
            jarfileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Jar", "*.jar"));
            jarfileChooser.setTitle(I18N.get("dubbo.env.dialog.chooser.title"));
            if (alertfile.exists())
                jarfileChooser.setInitialDirectory(alertfile);

            List<File> listFile = jarfileChooser.showOpenMultipleDialog(stage);
            if (null == listFile)
                return;

            StringBuilder path = new StringBuilder();
            for (File value : listFile) {
                if (value.exists()) {
                    path.append(value.getAbsolutePath()).append(",");
                }
            }
            if (!path.isEmpty()) {
                packageField.setText(path.toString());
            }
        });

        // Scan Button Logic
        scanButton.setOnAction(e -> {
            String paths = packageField.getText();
            if (StringUtils.isEmpty(paths)) {
                DialogUtil.showErrorInfo(I18N.get("dubbo.env.dialog.error.no_jar"), stage);
                return;
            }
            submitAsync(() -> {
                try {
                    String[] jarFile = paths.split(",");
                    List<String> interfaces = DubboConfigFileParseUtil.listInterface(jarFile,
                            DubboConfigFileParseUtil.getInformationFromJar(jarFile));
                    runOnUi(() -> DialogUtil.showSuccessInfo(
                            I18N.get("dubbo.env.dialog.scan.success",
                                    String.join("\n", interfaces)),
                            stage));
                } catch (Exception ex) {
                    runOnUi(() -> DialogUtil.showErrorInfo(
                            I18N.get("dubbo.env.dialog.scan.failed", ex.getMessage()), stage));
                }
            });
        });
    }

    private void refreshEnvList() {
        try {
            DubboWidgetDao dubboWidgetDao = new DubboWidgetDao();
            List<DubboEnvConfig> configs = dubboWidgetDao.queryAllEnvConfig();
            List<String> names = configs.stream().map(DubboEnvConfig::getEnvName)
                    .collect(Collectors.toList());
            runOnUi(() -> envListView.setItems(javafx.collections.FXCollections.observableArrayList(names)));
        } catch (Exception e) {
            logger.error("", e);
        }
    }

    private void loadEnvDetails(String envName) {
        try {
            DubboWidgetDao dubboWidgetDao = new DubboWidgetDao();
            DubboEnvConfig config = dubboWidgetDao.queryEnvConfig(envName);
            if (config != null) {
                envNameField.setText(config.getEnvName());
                zkAddressField.setText(config.getRegistryAddress());
                groupField.setText(config.getZkGroup());
                packageField.setText(config.getApiPackagePath());
            }
        } catch (Exception e) {
            logger.error("", e);
        }
    }

    private void clearForm() {
        envNameField.clear();
        zkAddressField.clear();
        groupField.clear();
        packageField.clear();
    }

    private void saveToDb(DubboEnvConfig config) throws Exception {
        DubboWidgetDao dao = new DubboWidgetDao();
        if (dao.queryEnvConfig(config.getEnvName()) != null) {
            dao.updateEnvConfig(config);
        } else {
            dao.insertEnvConfig(config);
        }
    }

    /**
     * 显示环境配置弹窗，相对 owner 居中（多屏/插件窗口时与当前操作窗口同屏）。
     * @param owner 所属窗口，可为 null
     */
    public void showAndWait(Window owner) throws IOException {
        if (stage == null) {
            stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.UNDECORATED);
            if (owner != null) stage.initOwner(owner);
            FXMLLoader fxmlLoader = new FXMLLoader(this.getClass().getResource(FXML_FILE));
            fxmlLoader.setController(this);
            fxmlLoader.setResources(I18N.getBundle(I18N.getLocale()));
            Scene scene = new Scene(fxmlLoader.load());

            // 确保根节点有 "root" 样式类
            if (envConfigRoot != null && !envConfigRoot.getStyleClass().contains("root")) {
                envConfigRoot.getStyleClass().add("root");
            }

            // Add ESC key handler
            scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                    stage.close();
                    event.consume();
                }
            });

            stage.setScene(scene);

            // 注册到 ThemeManager 以支持主题切换；相对 owner 居中
            stage.setOnShown(event -> {
                ThemeManager.getInstance().registerScene(stage.getScene());
                if (owner != null) DialogUtil.centerStageOnOwner(stage, owner);
            });

            // 当对话框关闭时注销
            stage.setOnHidden(event -> {
                ThemeManager.getInstance().unregisterScene(stage.getScene());
                dispose();
            });

            init();
        }
        stage.showAndWait();
    }

    private static ExecutorService newExecutor() {
        return Executors.newCachedThreadPool(r -> { Thread t = new Thread(r, "dubbo-env-worker"); t.setDaemon(true); return t; });
    }

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
