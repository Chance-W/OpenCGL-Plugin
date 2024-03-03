package com.xtool.opencgl.views;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import lombok.Getter;
import lombok.Setter;


/**
 * @author Chance.W
 */
@Getter
@Setter
public abstract class WebSourcesToolView implements Initializable {
	@FXML
	protected AnchorPane mainAnchorPane;
	@FXML
	protected Button homePageReturnHome;
	@FXML
	protected TextField urlTextField;
	@FXML
	protected Button jumpButton;
	@FXML
	protected Button browserOpenButton;
	@FXML
	protected Button downloadButton;
    @FXML
    protected BorderPane contentBorderPane;
    /*@FXML
	protected WebView showHtmlWebView;
    */

}