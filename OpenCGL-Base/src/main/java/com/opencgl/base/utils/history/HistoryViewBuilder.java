package com.opencgl.base.utils.history;

import com.opencgl.base.utils.i18n.BaseI18N;
import com.opencgl.base.model.HistoryItem;
import com.opencgl.base.service.HistoryService;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Generic builder for History View.
 * Provides a ready-made UI with Search, List, and Clear button.
 *
 * @param <T> The type of HistoryItem
 */
public class HistoryViewBuilder<T extends HistoryItem> {

    private static final Logger logger = LoggerFactory.getLogger(HistoryViewBuilder.class);

    // Dependencies
    private HistoryService service;
    private Callback<ListView<T>, ListCell<T>> cellFactory;

    // Configuration
    private boolean enableSearch = true;
    private boolean enableClearButton = true;
    private final String searchPrompt = BaseI18N.get("opencgl.base.search.prompt"); // "Search..."
    private final String clearButtonText = BaseI18N.get("opencgl.base.history.clear"); // "Clear History"
    private final String deleteMenuText = BaseI18N.get("opencgl.base.delete");  // "Delete"
    private CellDisplayConfig<T> cellDisplayConfig;
    // Removed: detailFieldsConfig, detailTextArea, detailCustomTextArea, detailContainer, detailPane
    private Consumer<T> restoreAction;
    private BorderPane mainContainer;
    private Node mainTop;
    private Node mainCenter;

    // Events
    private Consumer<T> onSelect;   // Click/Select
    private Consumer<T> onAnalyze;  // Double Click

    // State
    private ListView<T> listView;
    private final ObservableList<T> masterData = FXCollections.observableArrayList();
    private ObservableList<T> boundList; // Track bound list for unbinding
    private FilteredList<T> filteredData;

    public HistoryViewBuilder<T> service(HistoryService service) {
        this.service = service;
        return this;
    }
    public HistoryViewBuilder<T> cellDisplay(java.util.function.Consumer<CellDisplayConfig<T>> configurer) {
        this.cellDisplayConfig = new CellDisplayConfig<>();
        configurer.accept(this.cellDisplayConfig);
        return this;
    }


    public HistoryViewBuilder<T> cellFactory(Callback<ListView<T>, ListCell<T>> cellFactory) {
        this.cellFactory = cellFactory;
        return this;
    }

    public HistoryViewBuilder<T> onSelect(Consumer<T> onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    public HistoryViewBuilder<T> onAnalyze(Consumer<T> onAnalyze) {
        this.onAnalyze = onAnalyze;
        return this;
    }
    
    // Removed: detailFields(), detailTextArea(), detailContainer() methods
    
    /**
     * Set the restore action to be performed when double-clicking a history item.
     * This is a simplified alternative to manually providing onAnalyze logic.
     */
    public HistoryViewBuilder<T> restoreAction(Consumer<T> action) {
        this.restoreAction = action;
        return this;
    }
    
    /**
     * Set the main view container and nodes to return to after analyzing.
     * @param container The BorderPane container to restore
     * @param top The top node to restore
     * @param center The center node to restore
     */
    public HistoryViewBuilder<T> returnToMain(javafx.scene.layout.BorderPane container, javafx.scene.Node top, javafx.scene.Node center) {
        this.mainContainer = container;
        this.mainTop = top;
        this.mainCenter = center;
        return this;
    }

    public HistoryViewBuilder<T> enableSearch(boolean enable) {
        this.enableSearch = enable;
        return this;
    }

    public HistoryViewBuilder<T> enableClearButton(boolean enable) {
        this.enableClearButton = enable;
        return this;
    }

    public VBox build() {
        validate();
        
        // Setup defaults if configured
        setupDefaultCellFactory();
        setupDefaultOnAnalyze();



        VBox container = new VBox(5);
        container.getStyleClass().add("history-view-container");
        VBox.setVgrow(container, Priority.ALWAYS);

        // 1. Data Setup
        refresh();
        filteredData = new FilteredList<>(masterData, p -> true);

        // 2. Toolbar (Search + Clear)
        HBox toolbar = new HBox(5);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new javafx.geometry.Insets(5, 5, 0, 5));

        if (enableSearch) {
            TextField searchField = new TextField();
            searchField.setPromptText(searchPrompt);
            searchField.getStyleClass().add("search-field");
            HBox.setHgrow(searchField, Priority.ALWAYS);
            
            searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                filteredData.setPredicate(createPredicate(newValue));
            });
            toolbar.getChildren().add(searchField);
        }

        if (enableClearButton) {
            Button clearBtn = new Button();
            // Use icon if possible, or text
            // SVGPath or Graphic
            SVGPath icon = new SVGPath();
            icon.setContent("M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"); // Garbage can
            icon.setScaleX(0.7);
            icon.setScaleY(0.7);
            icon.setFill(javafx.scene.paint.Color.GRAY);
            clearBtn.setGraphic(icon);
            clearBtn.setTooltip(new Tooltip(clearButtonText));
            clearBtn.getStyleClass().add("icon-button");
            
            clearBtn.setOnAction(e -> {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle(BaseI18N.get("opencgl.base.confirm.title"));
                alert.setHeaderText(null);
                alert.setContentText(BaseI18N.get("opencgl.base.history.clear.confirm")); // "Are you sure to clear all history?"
                alert.showAndWait().ifPresent(result -> {
                    if (result == ButtonType.OK) {
                        service.clearHistory();
                        refresh();
                    }
                });
            });
            toolbar.getChildren().add(clearBtn);
        }

        if (!toolbar.getChildren().isEmpty()) {
            container.getChildren().add(toolbar);
        }

        // 3. ListView
        listView = new ListView<>(filteredData);
        VBox.setVgrow(listView, Priority.ALWAYS);
        if (cellFactory != null) {
            listView.setCellFactory(cellFactory);
        }

        // Events
        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && onSelect != null) {
                onSelect.accept(newVal);
            }
        });

        listView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && listView.getSelectionModel().getSelectedItem() != null) {
                if (onAnalyze != null) {
                    onAnalyze.accept(listView.getSelectionModel().getSelectedItem());
                }
            }
        });
        
        // Context Menu
        ContextMenu contextMenu = new ContextMenu();
        MenuItem deleteItem = new MenuItem(deleteMenuText);
        deleteItem.setOnAction(e -> {
            T selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                service.deleteHistory(selected);
                refresh();
            }
        });
        contextMenu.getItems().add(deleteItem);
        listView.setContextMenu(contextMenu);

        // Keyboard Delete
        listView.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.DELETE || event.getCode() == javafx.scene.input.KeyCode.BACK_SPACE) {
                T selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    service.deleteHistory(selected);
                    refresh();
                }
            }
        });

        container.getChildren().add(listView);

        return container;
    }

    private void validate() {
        if (service == null) throw new IllegalStateException("HistoryService must be set");
    }

    public void refresh() {
        // Unbind previous if exists
        if (boundList != null) {
            Bindings.unbindContent(masterData, boundList);
            boundList = null;
        }
        masterData.clear();

        List<T> history = (List<T>) service.getHistory(); // Safe cast if service is typed correctly
        if (history != null) {
            if (history instanceof ObservableList) {
                // Bind content to keep in sync (e.g. for async loading services)
                boundList = (ObservableList<T>) history;
                Bindings.bindContent(masterData, boundList);
            } else {
                // Snapshot for static lists
                masterData.addAll(history);
            }
        }
    }

    private Predicate<T> createPredicate(String searchText) {
        return item -> {
            if (searchText == null || searchText.isEmpty()) return true;
            String lower = searchText.toLowerCase();
            
            if (item.getSummary() != null && item.getSummary().toLowerCase().contains(lower)) return true;
            // Add more fields check if needed using Reflection or specific interface methods if T was more specific
            // For now, Summary is the main generic field.
            return false;
        };
    }
    
    /**
     * Setup default cell factory if cellDisplay is configured and cellFactory is not manually set.
     */
    private void setupDefaultCellFactory() {
        if (cellFactory == null && cellDisplayConfig != null) {
            final CellDisplayConfig<T> config = this.cellDisplayConfig;
            cellFactory = param -> new ListCell<>() {
                @Override
                protected void updateItem(T item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setGraphic(null);
                        setText(null);
                    } else {
                        setGraphic(buildDefaultCell(item, config));
                        setText(null);
                    }
                }
            };
        }
    }
    
    /**
     * Build default cell layout: two-line design with primary/secondary text and badge/timestamp.
     */
    private Node buildDefaultCell(T item, CellDisplayConfig<T> config) {
        VBox root = new VBox(2);
        root.setStyle("-fx-padding: 5;");
        
        // Line 1: Primary (bold blue) + Secondary (normal)
        HBox line1 = new HBox(5);
        line1.setAlignment(Pos.CENTER_LEFT);
        
        Label primary = new Label(config.getPrimaryText(item));
        primary.setStyle("-fx-font-weight: bold; -fx-text-fill: #007bff;");
        
        Label secondary = new Label(config.getSecondaryText(item));
        secondary.setStyle("-fx-text-fill: #333;");
        
        line1.getChildren().addAll(primary, secondary);
        
        // Line 2: Badge (green bold) + Timestamp (gray)
        HBox line2 = new HBox(10);
        line2.setAlignment(Pos.CENTER_LEFT);
        
        Label badge = new Label(config.getBadgeText(item));
        badge.setStyle("-fx-font-weight: bold; -fx-text-fill: green;");
        
        Label time = new Label(config.formatTimestamp(item));
        time.setStyle("-fx-text-fill: gray; -fx-font-size: 10px;");
        
        line2.getChildren().addAll(badge, time);
        
        root.getChildren().addAll(line1, line2);
        return root;
    }
    
    // Removed: setupDefaultOnSelect() method
    
    /**
     * Setup default onAnalyze handler if restoreAction is configured and onAnalyze is not manually set.
     */
    private void setupDefaultOnAnalyze() {
        if (onAnalyze == null && restoreAction != null) {
            final Consumer<T> action = this.restoreAction;
            final javafx.scene.layout.BorderPane container = this.mainContainer;
            final javafx.scene.Node top = this.mainTop;
            final javafx.scene.Node center = this.mainCenter;
            
            onAnalyze = item -> {
                action.accept(item);
                
                if (container != null) {
                    if (top != null) container.setTop(top);
                    if (center != null) container.setCenter(center);
                }
            };
        }
    }
}

