package com.opencgl.components;

import com.opencgl.model.RequestHistory;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.function.Consumer;

/**
 * 请求历史面板
 */
public class HistoryPanel extends VBox {
    
    private static final int MAX_HISTORY_SIZE = 50;
    
    private final ObservableList<RequestHistory> historyList = FXCollections.observableArrayList();
    private final ListView<RequestHistory> listView;
    private Consumer<RequestHistory> onSelectCallback;
    
    public HistoryPanel() {
        setSpacing(0);
        setPadding(new Insets(0));
        setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e0e0e0; -fx-border-width: 0 1 0 0;");
        
        // 标题栏
        HBox titleBar = new HBox(8);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(12, 12, 8, 12));
        titleBar.setStyle("-fx-background-color: #f1f3f4; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");
        
        Label titleLabel = new Label("History");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #202124; -fx-font-size: 14px;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button clearButton = new Button("Clear");
        clearButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #5f6368; -fx-font-size: 11px; -fx-cursor: hand;");
        clearButton.setOnAction(e -> historyList.clear());
        
        titleBar.getChildren().addAll(titleLabel, spacer, clearButton);
        
        // 搜索框
        TextField searchField = new TextField();
        searchField.setPromptText("Search history...");
        searchField.setStyle("-fx-background-color: white; -fx-border-color: #dadce0; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 6 8;");
        VBox.setMargin(searchField, new Insets(8, 12, 8, 12));
        
        // 历史列表
        listView = new ListView<>(historyList);
        listView.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        listView.setCellFactory(param -> new HistoryCell());
        VBox.setVgrow(listView, Priority.ALWAYS);
        
        // 双击加载历史记录
        listView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && onSelectCallback != null) {
                RequestHistory selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    onSelectCallback.accept(selected);
                }
            }
        });
        
        getChildren().addAll(titleBar, searchField, listView);
    }
    
    /**
     * 添加历史记录
     */
    public void addHistory(RequestHistory history) {
        historyList.add(0, history);
        
        // 限制历史记录数量
        while (historyList.size() > MAX_HISTORY_SIZE) {
            historyList.remove(historyList.size() - 1);
        }
    }
    
    /**
     * 设置选择回调
     */
    public void setOnSelect(Consumer<RequestHistory> callback) {
        this.onSelectCallback = callback;
    }
    
    /**
     * 历史记录单元格
     */
    private static class HistoryCell extends ListCell<RequestHistory> {
        @Override
        protected void updateItem(RequestHistory item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            
            VBox container = new VBox(2);
            container.setPadding(new Insets(8, 12, 8, 12));
            
            // 方法标签
            Label methodLabel = new Label(item.getMethod());
            methodLabel.setStyle(getMethodStyle(item.getMethod()));
            methodLabel.setPadding(new Insets(1, 6, 1, 6));
            
            // URL
            Label urlLabel = new Label(truncateUrl(item.getUrl()));
            urlLabel.setStyle("-fx-text-fill: #202124; -fx-font-size: 12px;");
            
            // 状态和时间
            HBox statusBox = new HBox(8);
            statusBox.setAlignment(Pos.CENTER_LEFT);
            
            Label statusLabel = new Label(item.getStatusDisplay());
            statusLabel.setStyle(item.isSuccess() 
                ? "-fx-text-fill: #1e8e3e; -fx-font-size: 11px;" 
                : "-fx-text-fill: #d93025; -fx-font-size: 11px;");
            
            statusBox.getChildren().add(statusLabel);
            
            container.getChildren().addAll(
                new HBox(8, methodLabel, urlLabel),
                statusBox
            );
            
            setGraphic(container);
            
            // 悬停效果
            setOnMouseEntered(e -> setStyle("-fx-background-color: #e8f0fe;"));
            setOnMouseExited(e -> setStyle("-fx-background-color: transparent;"));
        }
        
        private String getMethodStyle(String method) {
            String baseStyle = "-fx-font-size: 10px; -fx-font-weight: bold; -fx-background-radius: 3;";
            switch (method) {
                case "GET": return baseStyle + "-fx-background-color: #e6f4ea; -fx-text-fill: #1e8e3e;";
                case "POST": return baseStyle + "-fx-background-color: #fef7e0; -fx-text-fill: #f9ab00;";
                case "PUT": return baseStyle + "-fx-background-color: #e8f0fe; -fx-text-fill: #1a73e8;";
                case "DELETE": return baseStyle + "-fx-background-color: #fce8e6; -fx-text-fill: #d93025;";
                case "PATCH": return baseStyle + "-fx-background-color: #f3e8fd; -fx-text-fill: #9334e6;";
                default: return baseStyle + "-fx-background-color: #f1f3f4; -fx-text-fill: #5f6368;";
            }
        }
        
        private String truncateUrl(String url) {
            if (url == null) return "";
            if (url.length() > 40) {
                return url.substring(0, 40) + "...";
            }
            return url;
        }
    }
}
