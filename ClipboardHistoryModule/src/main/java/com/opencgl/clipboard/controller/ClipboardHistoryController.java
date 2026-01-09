package com.opencgl.clipboard.controller;

import com.opencgl.clipboard.i18n.I18N;
import com.opencgl.clipboard.model.ClipboardEntry;
import com.opencgl.clipboard.service.ClipboardService;
import javafx.application.Platform;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ResourceBundle;

public class ClipboardHistoryController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(ClipboardHistoryController.class);
    
    public BorderPane rootPane;
    public ToggleButton monitorToggle;
    public Button clearButton;
    public TextField searchField;
    public Label countLabel;
    public ListView<ClipboardEntry> historyList;
    public Button copyButton;
    public Button deleteButton;
    public Button favoriteButton;
    
    private ClipboardService clipboardService;
    private FilteredList<ClipboardEntry> filteredHistory;
    private volatile boolean disposed;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        clipboardService = new ClipboardService();
        
        setupUI();
        bindEvents();
        
        // 自动开始监听
        clipboardService.startMonitoring();
        monitorToggle.setSelected(true);
        initI18n();
    }

    private void initI18n() {
        monitorToggle.textProperty().bind(
            javafx.beans.binding.Bindings.when(monitorToggle.selectedProperty())
                .then(I18N.getBinding("btn.monitorOn"))
                .otherwise(I18N.getBinding("btn.monitorOff")));
        countLabel.setText(I18N.get("label.historyCount", 0));
    }

    private void setupUI() {
        // 设置过滤列表
        filteredHistory = new FilteredList<>(clipboardService.getHistory(), p -> true);
        historyList.setItems(filteredHistory);
        
        // 自定义单元格
        historyList.setCellFactory(lv -> new ClipboardCell());
        
        // 更新计数
        clipboardService.getHistory().addListener((javafx.collections.ListChangeListener<ClipboardEntry>) c -> {
            updateCount();
        });
        
        updateCount();
    }

    private void bindEvents() {
        // 监听切换
        monitorToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                clipboardService.startMonitoring();
            } else {
                clipboardService.stopMonitoring();
            }
        });

        // 搜索过滤
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredHistory.setPredicate(entry -> {
                if (newVal == null || newVal.isEmpty()) {
                    return true;
                }
                String lower = newVal.toLowerCase();
                return entry.getContent() != null && 
                       entry.getContent().toLowerCase().contains(lower);
            });
            updateCount();
        });

        // 清空历史
        clearButton.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, I18N.get("msg.confirmClear"));
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    clipboardService.clearHistory();
                }
            });
        });

        // 复制到剪贴板
        copyButton.setOnAction(e -> {
            ClipboardEntry selected = historyList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                clipboardService.copyToClipboard(selected);
            }
        });

        // 删除
        deleteButton.setOnAction(e -> {
            ClipboardEntry selected = historyList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                clipboardService.removeEntry(selected);
            }
        });

        // 收藏
        favoriteButton.setOnAction(e -> {
            ClipboardEntry selected = historyList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                selected.setFavorite(!selected.isFavorite());
                historyList.refresh();
            }
        });

        // 双击复制
        historyList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                ClipboardEntry selected = historyList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    clipboardService.copyToClipboard(selected);
                }
            }
        });
    }

    private void updateCount() {
        Platform.runLater(() -> {
            if (disposed) return;
            int total = clipboardService.getHistory().size();
            int filtered = filteredHistory.size();
            if (filtered == total) {
                countLabel.setText(I18N.get("label.historyCount", total));
            } else {
                countLabel.setText(I18N.get("label.historyCountFiltered", filtered, total));
            }
        });
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        if (clipboardService != null) {
            clipboardService.stopMonitoring();
        }
        if (monitorToggle != null) monitorToggle.setOnAction(null);
        if (clearButton != null) clearButton.setOnAction(null);
        if (copyButton != null) copyButton.setOnAction(null);
        if (deleteButton != null) deleteButton.setOnAction(null);
        if (favoriteButton != null) favoriteButton.setOnAction(null);
        if (historyList != null) historyList.setOnMouseClicked(null);
    }

    // 自定义单元格
    static class ClipboardCell extends ListCell<ClipboardEntry> {
        @Override
        protected void updateItem(ClipboardEntry item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            HBox cell = new HBox(10);
            cell.setStyle("-fx-padding: 5;");

            // 收藏星标
            Label star = new Label(item.isFavorite() ? "★" : "☆");
            star.setStyle("-fx-font-size: 16px; -fx-text-fill: " + 
                (item.isFavorite() ? "gold" : "gray") + ";");

            // 类型图标
            Label icon = new Label();
            switch (item.getType()) {
                case TEXT -> icon.setText("📝");
                case IMAGE -> icon.setText("📷");
                case FILE -> icon.setText("📁");
            }

            // 内容
            VBox content = new VBox(2);
            Label textLabel = new Label(item.getDisplayText());
            textLabel.setStyle("-fx-font-size: 12px;");
            Label timeLabel = new Label(item.getTimeAgo());
            timeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");
            content.getChildren().addAll(textLabel, timeLabel);

            cell.getChildren().addAll(star, icon, content);
            setGraphic(cell);
        }
    }
}
