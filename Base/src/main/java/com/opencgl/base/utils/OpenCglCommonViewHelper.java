package com.opencgl.base.utils;

import java.util.Objects;

import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

/**
 * @author Chance.W
 * @version 1.0
 * @CreateDate 2024/02/22 09:38
 * @since v9.0
 */
public class OpenCglCommonViewHelper {

    public WebView loadWebView(String url) {
        WebView browser = new WebView();
        WebEngine webEngine = browser.getEngine();
        if (url.startsWith("http")) {
            webEngine.load(url);
        }
        else {
            webEngine.load(Objects.requireNonNull(OpenCglCommonViewHelper.class.getResource(url)).toExternalForm());
        }
        return browser;
    }
}
