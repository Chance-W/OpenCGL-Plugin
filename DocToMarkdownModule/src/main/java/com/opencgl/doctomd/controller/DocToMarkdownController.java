package com.opencgl.doctomd.controller;

import com.opencgl.base.theme.ThemeManager;
import com.opencgl.base.utils.CommitOnBlurTableCell;
import com.opencgl.doctomd.i18n.I18N;
import com.opencgl.doctomd.model.FileInfo;
import com.opencgl.doctomd.service.DocToMarkdownService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;

public class DocToMarkdownController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(DocToMarkdownController.class);

    @FXML
    private VBox rootPane;
    @FXML
    private Button addFilesButton;
    @FXML
    private Button removeButton;
    @FXML
    private Button clearButton;
    @FXML
    private TableView<FileInfo> fileTable;
    @FXML
    private TableColumn<FileInfo, String> colFilename;
    @FXML
    private TableColumn<FileInfo, String> colFormat;
    @FXML
    private TableColumn<FileInfo, String> colOutputDir;
    @FXML
    private TableColumn<FileInfo, String> colMediaDir;
    @FXML
    private TableColumn<FileInfo, String> colStatus;
    @FXML
    private CheckBox extractImagesCheck;
    @FXML
    private CheckBox ocrCheck;
    @FXML
    private ToggleGroup pathGroup;
    @FXML
    private RadioButton radioRelative;
    @FXML
    private RadioButton radioAbsolute;
    @FXML
    private Label tipLabel;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label progressLabel;
    @FXML
    private Button convertButton;
    @FXML
    private TextArea logArea;

    private final ObservableList<FileInfo> fileList = FXCollections.observableArrayList();
    private final DocToMarkdownService service = new DocToMarkdownService();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> conversionFuture;
    private volatile boolean converting = false;
    private volatile boolean disposed;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (rootPane != null && !rootPane.getStyleClass().contains("root")) {
            rootPane.getStyleClass().add("root");
        }
        Scene scene = rootPane != null ? rootPane.getScene() : null;
        if (scene != null) {
            ThemeManager.getInstance().registerScene(scene);
        }

        ToggleGroup pathGroup = new ToggleGroup();
        radioRelative.setToggleGroup(pathGroup);
        radioAbsolute.setToggleGroup(pathGroup);
        radioRelative.setSelected(true);
        initI18n();
        initTable();
        tipLabel.setText(I18N.get("tip"));
    }

    private void initI18n() {
        addFilesButton.textProperty().bind(I18N.getBinding("btn.addFiles"));
        removeButton.textProperty().bind(I18N.getBinding("btn.remove"));
        clearButton.textProperty().bind(I18N.getBinding("btn.clear"));
        convertButton.setText(I18N.get("btn.convert"));
        colFilename.setText(I18N.get("col.filename"));
        colFormat.setText(I18N.get("col.format"));
        colOutputDir.setText(I18N.get("col.outputDir"));
        colMediaDir.setText(I18N.get("col.mediaDir"));
        colStatus.setText(I18N.get("col.status"));
        extractImagesCheck.textProperty().bind(I18N.getBinding("check.extractImages"));
        ocrCheck.textProperty().bind(I18N.getBinding("check.ocr"));
        radioRelative.textProperty().bind(I18N.getBinding("radio.relative"));
        radioAbsolute.textProperty().bind(I18N.getBinding("radio.absolute"));
        tipLabel.setText(I18N.get("tip"));
    }

    private void initTable() {
        fileTable.setItems(fileList);
        colFilename.setCellValueFactory(cell -> {
            FileInfo f = cell.getValue();
            return new javafx.beans.property.SimpleStringProperty(f != null ? f.getFileName() : "");
        });
        colFormat.setCellValueFactory(cell -> {
            FileInfo f = cell.getValue();
            String ext = f != null ? f.getExtension() : "";
            return new javafx.beans.property.SimpleStringProperty(ext.toUpperCase());
        });
        colOutputDir.setCellValueFactory(new PropertyValueFactory<>("outputDir"));
        colMediaDir.setCellValueFactory(new PropertyValueFactory<>("mediaDir"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        colMediaDir.setCellFactory(CommitOnBlurTableCell.forStringColumn());
        colMediaDir.setOnEditCommit(event -> {
            String value = event.getNewValue();
            event.getRowValue().setMediaDir(value != null && !value.trim().isEmpty() ? value.trim() : "media");
        });
        colMediaDir.setEditable(true);
        fileTable.setEditable(true);

        colOutputDir.setCellFactory(col -> {
            TableCell<FileInfo, String> cell = new TableCell<>();
            cell.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !cell.isEmpty()) {
                    FileInfo f = cell.getTableRow().getItem();
                    if (f != null) chooseOutputDir(f);
                }
            });
            cell.itemProperty().addListener((o, a, b) -> cell.setText(b));
            return cell;
        });

        fileTable.setRowFactory(tv -> {
            TableRow<FileInfo> row = new TableRow<>();
            final javafx.beans.value.ChangeListener<String>[] statusListener = new javafx.beans.value.ChangeListener[1];
            row.itemProperty().addListener((o, oldItem, item) -> {
                if (oldItem != null && statusListener[0] != null) {
                    oldItem.statusProperty().removeListener(statusListener[0]);
                }
                if (item != null) {
                    item.setStatus(I18N.get("status.pending"));
                    Runnable updateTip = () -> {
                        String path = item.getFilePath();
                        String out = item.getOutputDir();
                        String media = item.getMediaDir();
                        String status = item.getStatus();
                        row.setTooltip(new Tooltip(
                            I18N.get("col.filename") + ": " + (path != null ? path : "") + "\n"
                                + I18N.get("col.outputDir") + ": " + (out != null ? out : "") + "\n"
                                + I18N.get("col.mediaDir") + ": " + (media != null ? media : "") + "\n"
                                + I18N.get("col.status") + ": " + (status != null ? status : "")
                        ));
                    };
                    updateTip.run();
                    statusListener[0] = (a, b, c) -> updateTip.run();
                    item.statusProperty().addListener(statusListener[0]);
                }
                else {
                    row.setTooltip(null);
                }
            });
            return row;
        });

        for (FileInfo f : fileList) {
            f.setStatus(I18N.get("status.pending"));
        }
    }

    private void chooseOutputDir(FileInfo fileInfo) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle(I18N.get("msg.selectOutputDir"));
        String current = fileInfo.getOutputDir();
        if (current != null && !current.isEmpty()) {
            File dir = new File(current);
            if (dir.isDirectory()) dc.setInitialDirectory(dir);
        }
        File chosen = dc.showDialog(rootPane.getScene().getWindow());
        if (chosen != null) {
            fileInfo.setOutputDir(chosen.getAbsolutePath());
            log(I18N.get("msg.selectOutputDir") + ": " + chosen.getAbsolutePath());
        }
    }

    @FXML
    private void onAddFiles() {
        FileChooser fc = new FileChooser();
        fc.setTitle(I18N.get("msg.selectFiles"));
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter(I18N.get("msg.filter").replaceAll("\\|.*", "").trim(), "*.pdf", "*.doc", "*.docx", "*.html", "*.htm"),
            new FileChooser.ExtensionFilter("*.*", "*.*")
        );
        List<File> files = fc.showOpenMultipleDialog(rootPane.getScene().getWindow());
        if (files == null || files.isEmpty()) return;
        for (File f : files) {
            String path = f.getAbsolutePath();
            String parent = f.getParent();
            String baseName = f.getName();
            int dot = baseName.lastIndexOf('.');
            String nameNoExt = dot > 0 ? baseName.substring(0, dot) : baseName;
            String outputDir = parent + File.separator + nameNoExt + "_result";
            fileList.add(new FileInfo(path, outputDir, "media"));
        }
        log(I18N.get("msg.added", files.size()));
    }

    @FXML
    private void onRemove() {
        FileInfo selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            fileList.remove(selected);
            log(I18N.get("msg.removed"));
        }
    }

    @FXML
    private void onClear() {
        fileList.clear();
        log(I18N.get("msg.cleared"));
    }

    @FXML
    private void onConvert() {
        if (fileList.isEmpty()) {
            showAlert(I18N.get("msg.noFiles"));
            return;
        }
        if (logArea != null) logArea.end();
        service.setLog(this::log);
        log(I18N.get("tool.check"));
        boolean hasPdfTools = service.checkTool("pdftotext") && service.checkTool("pdfimages");
        if (!hasPdfTools) {
            log(I18N.get("tool.missing", "pdftotext/pdfimages"));
            String pdfHints = service.getInstallHintsAllPlatforms("pdftotext");
            if (!pdfHints.isEmpty()) {
                for (String line : pdfHints.split("\n")) log("  " + line);
            }
            String alertMsg = I18N.get("error.missing_tools") + "\n\n" + I18N.get("tool.install_hints_title") + ":\n" + pdfHints.replace("\n", "\n  ");
            showAlert(alertMsg);
            return;
        }
        log(I18N.get("tool.ok", "pdftotext"));
        log(I18N.get("tool.ok", "pdfimages"));
        if (!service.checkTool("pandoc")) {
            log(I18N.get("tool.pandoc_optional"));
            String h = service.getInstallHintsAllPlatforms("pandoc");
            if (!h.isEmpty()) { for (String line : h.split("\n")) log("  " + line); }
        } else log(I18N.get("tool.ok", "pandoc"));
        if (!service.checkTool("soffice")) {
            log(I18N.get("tool.soffice_optional"));
            String h = service.getInstallHintsAllPlatforms("soffice");
            if (!h.isEmpty()) { for (String line : h.split("\n")) log("  " + line); }
        } else log(I18N.get("tool.ok", "soffice"));
        if (ocrCheck.isSelected() && !service.checkTool("tesseract")) {
            log(I18N.get("tool.tesseract_optional"));
            String h = service.getInstallHintsAllPlatforms("tesseract");
            if (!h.isEmpty()) { for (String line : h.split("\n")) log("  " + line); }
        } else if (service.checkTool("tesseract")) log(I18N.get("tool.ok", "tesseract"));
        log("");
        if (logArea != null) logArea.end();

        converting = true;
        convertButton.setText(I18N.get("btn.converting"));
        setButtonsDisabled(true);
        progressBar.setProgress(0);
        if (progressLabel != null) progressLabel.setText("0%");
        for (FileInfo f : fileList) {
            f.setStatus(I18N.get("status.converting"));
        }

        conversionFuture = executor.submit(() -> {
            int totalFiles = fileList.size();
            int totalStepsCount = 0;
            for (FileInfo f : fileList) {
                totalStepsCount += DocToMarkdownService.getStepsForExtension(f.getExtension());
            }
            if (totalStepsCount <= 0) totalStepsCount = 1;
            final int totalSteps = totalStepsCount;
            final int[] currentStep = {0};
            Runnable stepCompleted = () -> {
                currentStep[0]++;
                CountDownLatch latch = new CountDownLatch(1);
                Platform.runLater(() -> {
                    if (disposed) {
                        latch.countDown();
                        return;
                    }
                    double p = currentStep[0] / (double) totalSteps;
                    progressBar.setProgress(p);
                    if (progressLabel != null) progressLabel.setText(String.format("%.0f%%", p * 100));
                    latch.countDown();
                });
                try {
                    latch.await(2, TimeUnit.SECONDS);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };
            int success = 0;
            for (int i = 0; i < totalFiles; i++) {
                FileInfo f = fileList.get(i);
                CountDownLatch startLatch = new CountDownLatch(1);
                Platform.runLater(() -> {
                    if (disposed) {
                        startLatch.countDown();
                        return;
                    }
                    f.setStatus(I18N.get("status.converting"));
                    startLatch.countDown();
                });
                try {
                    startLatch.await(2, TimeUnit.SECONDS);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                boolean ok = service.convert(
                    f,
                    extractImagesCheck.isSelected(),
                    radioRelative == null || radioRelative.isSelected(),
                    ocrCheck.isSelected(),
                    msg -> Platform.runLater(() -> {
                        if (!disposed) log(msg);
                    }),
                    stepCompleted
                );
                if (ok) success++;
                final String status = ok ? I18N.get("status.success") : I18N.get("status.fail");
                Platform.runLater(() -> {
                    if (!disposed) f.setStatus(status);
                });
            }
            final int s = success;
            Platform.runLater(() -> {
                if (disposed) return;
                progressBar.setProgress(1);
                if (progressLabel != null) progressLabel.setText("100%");
                log(I18N.get("msg.done", s, totalFiles - s));
                converting = false;
                convertButton.setText(I18N.get("btn.convert"));
                setButtonsDisabled(false);
                showAlert(I18N.get("msg.done", s, totalFiles - s));
            });
        });
    }

    private void setButtonsDisabled(boolean disabled) {
        addFilesButton.setDisable(disabled);
        removeButton.setDisable(disabled);
        clearButton.setDisable(disabled);
        convertButton.setDisable(disabled);
    }

    private void log(String msg) {
        Platform.runLater(() -> {
            if (!disposed && logArea != null) {
                logArea.appendText(msg + "\n");
                logArea.end();
            }
        });
    }

    private void showAlert(String content) {
        Platform.runLater(() -> {
            if (disposed) return;
            Window owner = rootPane != null && rootPane.getScene() != null ? rootPane.getScene().getWindow() : null;
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setContentText(content);
            if (owner != null) {
                alert.initOwner(owner);
            }
            alert.getDialogPane().getStyleClass().add("root");
            alert.setOnShown(e -> {
                Scene scene = alert.getDialogPane().getScene();
                if (scene != null) {
                    ThemeManager.getInstance().registerScene(scene);
                }
            });
            alert.setOnHidden(e -> {
                Scene s = alert.getDialogPane().getScene();
                if (s != null) ThemeManager.getInstance().unregisterScene(s);
            });
            alert.show();
        });
    }

    @FXML
    private void onExit() {
        Platform.exit();
    }

    @FXML
    private void onAbout() {
        Window owner = rootPane != null && rootPane.getScene() != null ? rootPane.getScene().getWindow() : null;
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(I18N.get("about.title"));
        alert.setHeaderText(I18N.get("label.name"));
        alert.setContentText(I18N.get("about.text"));
        if (owner != null) alert.initOwner(owner);
        alert.getDialogPane().getStyleClass().add("root");
        alert.setOnShown(e -> {
            javafx.scene.Scene s = alert.getDialogPane().getScene();
            if (s != null) ThemeManager.getInstance().registerScene(s);
        });
        alert.setOnHidden(e -> {
            javafx.scene.Scene s = alert.getDialogPane().getScene();
            if (s != null) ThemeManager.getInstance().unregisterScene(s);
        });
        alert.showAndWait();
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        converting = false;
        if (conversionFuture != null) conversionFuture.cancel(true);
        try {
            service.cancel();
        } catch (RuntimeException e) {
            logger.warn("Failed to stop document converter process", e);
        }
        executor.shutdownNow();
    }
}
