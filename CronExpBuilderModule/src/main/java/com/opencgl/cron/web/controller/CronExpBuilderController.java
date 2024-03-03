package com.opencgl.cron.web.controller;

import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;

import com.opencgl.cron.web.views.CronExpBuilderView;
import javafx.application.Platform;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

/**
 * @author Chance.W
 * @version 1.0
 * @CreateDate 2024/02/22 09:32
 * @since v9.0
 */
public class CronExpBuilderController extends CronExpBuilderView {

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        init();
    }

    private void init() {
        Platform.runLater(() -> {
            String url = "/cron/index.htm";
            WebView browser = new WebView();
            WebEngine webEngine = browser.getEngine();
            webEngine.load(Objects.requireNonNull(CronExpBuilderController.class.getResource(url)).toExternalForm());
            mainStackPane.getChildren().setAll(browser);
        });
    }
}
