package com.opencgl.base.view;

import com.opencgl.base.utils.i18n.BaseI18N;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Generic container for sidebar request management.
 * Integrates "Saved" (Collection/Tree) and "History" views.
 */
public class RequestManagerView extends VBox {

    private final TabPane tabPane;

    public TabPane getTabPane() {
        return tabPane;
    }
    private final Tab collectionTab;
    private final Tab historyTab;

    public RequestManagerView() {
        this.getStyleClass().add("request-manager-view");
        VBox.setVgrow(this, Priority.ALWAYS);
        this.setFillWidth(true);

        // Initialize TabPane
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getStyleClass().add("request-manager-tabs");
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        // Sidebar style generally fills width
        tabPane.setMaxWidth(Double.MAX_VALUE);

        // Tabs
        collectionTab = new Tab(BaseI18N.getOrDefault("opencgl.base.tab.saved", "Collections"));
        historyTab = new Tab(BaseI18N.getOrDefault("opencgl.base.tab.history", "History"));

        tabPane.getTabs().addAll(collectionTab, historyTab);

        this.getChildren().add(tabPane);
    }

    public void setCollectionView(Node view) {
        collectionTab.setContent(view);
    }

    public void setHistoryView(Node view) {
        historyTab.setContent(view);
    }
    
    public void setCollectionTabTitle(String title) {
        collectionTab.setText(title);
    }

    public void setHistoryTabTitle(String title) {
        historyTab.setText(title);
    }

    public void selectCollectionTab() {
        tabPane.getSelectionModel().select(collectionTab);
    }

    public void selectHistoryTab() {
        tabPane.getSelectionModel().select(historyTab);
    }

}
