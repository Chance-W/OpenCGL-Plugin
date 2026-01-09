package com.opencgl.checksum.controller;

import com.opencgl.checksum.i18n.I18N;
import com.opencgl.checksum.service.ChecksumService;
import com.opencgl.checksum.service.ChecksumService.*;
import com.opencgl.checksum.views.FileChecksumView;
import javafx.application.Platform;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.Initializable;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class FileChecksumController extends FileChecksumView implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(FileChecksumController.class);

    private final ChecksumService checksumService = new ChecksumService();
    private final ObservableList<FileHashResult> results = FXCollections.observableArrayList();
    private volatile boolean disposed;

    /** 持久化引用，防止 GC 导致 locale 监听失效 */
    @SuppressWarnings("FieldCanBeLocal")
    private javafx.beans.binding.StringBinding statusReadyBinding;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupTable();
        setupDragDrop();
        algorithmComboBox.selectFirst();
        bindEvents();
        bindI18n();
        setStatus(I18N.get("label.status_ready"));
    }

    private void bindI18n() {
        titleLabel.textProperty().bind(I18N.getBinding("label.title"));
        algoLabel.textProperty().bind(I18N.getBinding("label.algo"));
        selectFileButton.textProperty().bind(I18N.getBinding("button.select_file"));
        selectFolderButton.textProperty().bind(I18N.getBinding("button.select_folder"));
        clearButton.textProperty().bind(I18N.getBinding("button.clear"));
        exportButton.textProperty().bind(I18N.getBinding("button.export"));
        resultsLabel.textProperty().bind(I18N.getBinding("label.results"));
        dragDropLabel.textProperty().bind(I18N.getBinding("label.drag_drop"));
        compareLabel.textProperty().bind(I18N.getBinding("label.compare"));
        hash1Label.textProperty().bind(I18N.getBinding("label.hash1"));
        hash2Label.textProperty().bind(I18N.getBinding("label.hash2"));
        compareButton.textProperty().bind(I18N.getBinding("button.compare"));

        filenameColumn.textProperty().bind(I18N.getBinding("column.filename"));
        sizeColumn.textProperty().bind(I18N.getBinding("column.size"));
        algoColumn.textProperty().bind(I18N.getBinding("column.algo"));
        hashColumn.textProperty().bind(I18N.getBinding("column.hash"));

        file1HashField.promptTextProperty().bind(I18N.getBinding("prompt.hash1"));
        file2HashField.promptTextProperty().bind(I18N.getBinding("prompt.hash2"));

        // statusLabel 是动态文本（业务逻辑随时覆盖），不能直接 bind；
        // 改用持久化 StringBinding 监听 locale 变化，切换语言时重置为"就绪"提示。
        statusReadyBinding = I18N.getBinding("label.status_ready");
        statusReadyBinding.addListener((obs, oldVal, newVal) -> setStatus(newVal != null ? newVal : ""));
    }

    @SuppressWarnings("unchecked")
    private void setupTable() {
        TableView<FileHashResult> table = (TableView<FileHashResult>) resultsTable;
        table.setItems(results);
        table.getSelectionModel().setCellSelectionEnabled(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Context Menu for Copy
        ContextMenu menu = new ContextMenu();
        MenuItem copyItem = new MenuItem();
        copyItem.textProperty().bind(I18N.getBinding("menu.copy"));
        copyItem.setOnAction(e -> copySelection());
        menu.getItems().add(copyItem);
        table.setContextMenu(menu);

        // Keyboard Copy
        table.setOnKeyPressed(event -> {
            if (new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.C,
                    javafx.scene.input.KeyCombination.SHORTCUT_DOWN).match(event)) {
                copySelection();
            }
        });

        TableColumn<FileHashResult, String> nameCol = (TableColumn<FileHashResult, String>) table.getColumns().get(0);
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().fileName()));

        TableColumn<FileHashResult, String> sizeCol = (TableColumn<FileHashResult, String>) table.getColumns().get(1);
        sizeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFormattedSize()));

        TableColumn<FileHashResult, String> algCol = (TableColumn<FileHashResult, String>) table.getColumns().get(2);
        algCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().algorithm()));

        TableColumn<FileHashResult, String> hashCol = (TableColumn<FileHashResult, String>) table.getColumns().get(3);
        hashCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().hash()));
    }

    private void copySelection() {
        TableView<FileHashResult> table = (TableView<FileHashResult>) resultsTable;
        ObservableList<javafx.scene.control.TablePosition> posList = table.getSelectionModel().getSelectedCells();
        if (posList.isEmpty())
            return;

        StringBuilder sb = new StringBuilder();
        int lastRow = -1;

        // Sort by row then column usually handled by TableView selection order, but
        // just to be safe handled simply
        for (javafx.scene.control.TablePosition pos : posList) {
            int row = pos.getRow();
            int col = pos.getColumn();

            // This is a simple impl, ideally we group by row
            Object val = table.getColumns().get(col).getCellData(row);
            if (val != null) {
                sb.append(val).append(" "); // Space separated for simplicity or newline? User asked for copy info.
            }
        }

        // Better implementation: iterate selected items if full row selection, or cell
        // selection logic
        // Let's use a simpler approach that works for typical single cell or full row
        // usage
        // Copy the specific HASH if that's what user likely wants, or the full row
        // info.

        // Refined Copy Logic:
        // If cell selection is enabled, we copy text from selected cells.
        ClipboardContent content = new ClipboardContent();

        StringBuilder clipboardString = new StringBuilder();
        for (TablePosition p : posList) {
            Object cellData = table.getColumns().get(p.getColumn()).getCellData(p.getRow());
            if (cellData != null)
                clipboardString.append(cellData);
            clipboardString.append("\n"); // Newline for each cell/row
        }

        content.putString(clipboardString.toString().trim());
        Clipboard.getSystemClipboard().setContent(content);
        setStatus(I18N.get("msg.copied"));
    }

    private void setupDragDrop() {
        resultsTable.setOnDragOver(event -> {
            if (event.getGestureSource() != resultsTable && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        resultsTable.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                List<File> files = db.getFiles();
                onFilesDropped(files.toArray(new File[0]));
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void bindEvents() {
        selectFileButton.setOnAction(e -> onSelectFile());
        selectFolderButton.setOnAction(e -> onSelectFolder());
        clearButton.setOnAction(e -> onClear());
        exportButton.setOnAction(e -> onExport());
        compareButton.setOnAction(e -> onCompare());
    }

    private void onSelectFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18N.get("msg.select_file"));
        File file = chooser.showOpenDialog(mainStackPane.getScene().getWindow());

        if (file != null) {
            calculateFileHash(file);
        }
    }

    private void onSelectFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(I18N.get("msg.select_folder"));
        File dir = chooser.showDialog(mainStackPane.getScene().getWindow());

        if (dir != null) {
            File[] files = dir.listFiles(File::isFile);
            if (files != null && files.length > 0) {
                onFilesDropped(files);
            }
        }
    }

    private void calculateFileHash(File file) {
        HashAlgorithm algorithm = HashAlgorithm.valueOf(algorithmComboBox.getValue());
        setStatus(I18N.get("msg.calculating", file.getName()));

        checksumService.calculateHash(file, algorithm)
                .thenAccept(result -> Platform.runLater(() -> {
                    if (disposed) return;
                    results.add(result);
                    setStatus(I18N.get("msg.calc_done"));
                }));
    }

    private void onFilesDropped(File[] files) {
        HashAlgorithm algorithm = HashAlgorithm.valueOf(algorithmComboBox.getValue());
        setStatus(I18N.get("msg.batch_calculating"));

        checksumService.calculateBatch(files, algorithm,
                result -> Platform.runLater(() -> { if (!disposed) results.add(result); }),
                () -> Platform.runLater(() -> { if (!disposed) setStatus(I18N.get("msg.batch_done", files.length)); }));
    }

    private void onClear() {
        results.clear();
        file1HashField.clear();
        file2HashField.clear();
        compareResultLabel.setText("");
        setStatus(I18N.get("msg.cleared"));
    }

    private void onCompare() {
        String hash1 = file1HashField.getText().trim();
        String hash2 = file2HashField.getText().trim();

        if (hash1.isEmpty() || hash2.isEmpty()) {
            compareResultLabel.setText(I18N.get("msg.input_two_hashes"));
            compareResultLabel.setStyle("-fx-text-fill: #e74c3c;");
            return;
        }

        boolean match = checksumService.compareHashes(hash1, hash2);
        if (match) {
            compareResultLabel.setText(I18N.get("msg.hash_same"));
            compareResultLabel.setStyle("-fx-text-fill: #27ae60;");
        } else {
            compareResultLabel.setText(I18N.get("msg.hash_diff"));
            compareResultLabel.setStyle("-fx-text-fill: #e74c3c;");
        }
    }

    private void setStatus(String status) {
        statusLabel.setText(status);
    }

    private void onExport() {
        if (results.isEmpty()) {
            setStatus(I18N.get("msg.no_export"));
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18N.get("msg.export_title"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18N.get("msg.csv_filter"), "*.csv"));
        fileChooser.setInitialFileName("checksum_results.csv");

        File file = fileChooser.showSaveDialog(mainStackPane.getScene().getWindow());
        if (file == null) {
            return;
        }

        setStatus(I18N.get("msg.exporting"));

        // Simple CSV Export running on background thread
        checksumService.runAsync(() -> {
            try (java.io.PrintWriter writer = new java.io.PrintWriter(
                    new java.io.FileWriter(file, java.nio.charset.StandardCharsets.UTF_8))) {
                // Header
                writer.println(I18N.get("msg.csv_header"));

                // Data
                for (FileHashResult result : results) {
                    writer.println(String.format("%s,%s,%s,%s",
                            escapeCsv(result.fileName()),
                            result.getFormattedSize(),
                            result.algorithm(),
                            result.hash()));
                }

                Platform.runLater(() -> { if (!disposed) setStatus(I18N.get("msg.export_success", file.getName())); });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (disposed) return;
                    setStatus(I18N.get("msg.export_failed", e.getMessage()));
                    logger.error(I18N.get("msg.export_failed", ""), e);
                });
            }
        });
    }

    private String escapeCsv(String text) {
        if (text == null)
            return "";
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        checksumService.dispose();
    }
}
