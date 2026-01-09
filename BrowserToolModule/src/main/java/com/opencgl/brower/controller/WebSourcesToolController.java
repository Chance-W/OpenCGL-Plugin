package com.opencgl.brower.controller;


import java.awt.*;
import java.net.URI;
import java.net.URL;
import java.util.ResourceBundle;

import org.apache.commons.lang.StringUtils;

import com.opencgl.base.utils.TooltipUtil;
import com.opencgl.brower.i18n.I18N;
import com.opencgl.brower.utils.WebSourcesToolService;
import com.opencgl.brower.views.WebSourcesToolView;
import javafx.application.Platform;
import javafx.concurrent.Worker.State;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author Chance.W
 */
@Getter
@Setter
public class WebSourcesToolController extends WebSourcesToolView {

    private static final Logger log = LoggerFactory.getLogger(WebSourcesToolController.class);

    private WebSourcesToolService webSourcesToolService = new WebSourcesToolService(this);
    private WebView showHtmlWebView;
    private volatile boolean disposed;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initView();
        initEvent();
        initService();
        initI18n();
    }

    private void initI18n() {
        jumpButton.textProperty().bind(I18N.getBinding("button.jump"));
        homePageReturnHome.textProperty().bind(I18N.getBinding("button.home"));
        browserOpenButton.textProperty().bind(I18N.getBinding("button.open_browser"));
    }

    private void initView() {
        urlTextField.setText("https://www.tool-graphical.top/");
    }

    private void initEvent() {
        Platform.runLater(() -> {
            if (disposed) return;
            showHtmlWebView = new WebView();
            contentBorderPane.setCenter(showHtmlWebView);
            BorderPane.setMargin(showHtmlWebView,new Insets(10,0,0,0));
            WebEngine webEngine = showHtmlWebView.getEngine();
            webEngine.getLoadWorker().stateProperty().addListener((obs, oldValue, newValue) -> {
                if (newValue == State.SUCCEEDED) {
                    log.info("finished loading");
                }
            }); // addListener()
            urlTextField.setOnKeyReleased(event -> {
                if (event.getCode() == KeyCode.ENTER) {
                    jumpAction();
                }
            });
        });
    }

    private void initService() {
    }

    @FXML
    private void returnHomePage() {
        //showHrmlWebView.getEngine().load("http://47.102.132.125");
        //showHrmlWebView.getEngine().load("http://www.tool-graphical.top:9102/");
        urlTextField.setText("https://www.tool-graphical.top/");
        showHtmlWebView.getEngine().load("https://www.tool-graphical.top/");
        //showHrmlWebView.getEngine().load("http://www.baidu.com");
    }

    @FXML
    private void jumpAction() {
        if (StringUtils.isEmpty(urlTextField.getText())) {
            TooltipUtil.showToast(mainAnchorPane, "请输入网站！！！");
            return;
        }
        showHtmlWebView.getEngine().load(urlTextField.getText());
    }

    @FXML
    private void browserOpenAction() {
        if (StringUtils.isEmpty(urlTextField.getText())) {
            TooltipUtil.showToast(mainAnchorPane, "请输入网站！！！");
            return;
        }
        try {
            Desktop desktop = Desktop.getDesktop();
            desktop.browse(new URI(urlTextField.getText()));
            new URI(urlTextField.getText());
        }
        catch (Exception e1) {
            TooltipUtil.showToast(mainAnchorPane, "输入Url有误！" + e1.getMessage());
            log.error(e1.getMessage());
        }
    }

    @SuppressWarnings("unused")
    @FXML
    private void downloadAction(ActionEvent event) throws Exception {
        webSourcesToolService.downloadHtmlSources();
        WebEngine webEngine = showHtmlWebView.getEngine();
//		WebPage page = (WebPage) FieldUtils.readDeclaredField(webEngine, "page", true);
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        urlTextField.setOnKeyReleased(null);
        webSourcesToolService.dispose();
        webSourcesToolService = null;
        if (showHtmlWebView != null) {
            showHtmlWebView.getEngine().load("about:blank");
            contentBorderPane.setCenter(null);
            showHtmlWebView = null;
        }
    }
}
