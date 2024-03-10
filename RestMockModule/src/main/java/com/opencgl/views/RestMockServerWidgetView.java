package com.opencgl.views;

import com.opencgl.base.controls.CustomTextArea;
import com.opencgl.model.RestMockTableBean;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXProgressBar;
import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.fxml.FXML;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

/**
 * @author Chance.W
 */
public class RestMockServerWidgetView {
    @FXML
    protected StackPane mainStackPane;
    @FXML
    protected SplitPane splitPane;
    @FXML
    protected MFXButton addButton;
    @FXML
    protected MFXButton startButton;
    @FXML
    protected MFXButton stopButton;
    @FXML
    protected TableView<RestMockTableBean> tableViewMain;
    @FXML
    protected TableColumn<RestMockTableBean, Boolean> isEnabledTableColumn;
    @FXML
    protected TableColumn<RestMockTableBean, String> contentPathTableColumn;
    @FXML
    protected TableColumn<RestMockTableBean, String> responseHeaderTableColumn;
    @FXML
    protected TableColumn<RestMockTableBean, String> responseContentTableColumn;
    @FXML
    protected TableColumn<RestMockTableBean, String> desTableColumn;
    @FXML
    protected TableColumn<RestMockTableBean, String> stateTableColumn;
    @FXML
    protected CustomTextArea outputTextArea;
    @FXML
    protected MFXTextField portTextField;
    @FXML
    protected MFXTextField contentPath;
    @FXML
    protected MFXTextField responseHeader;
    @FXML
    protected CustomTextArea responseContent;
    @FXML
    protected MFXTextField des;
    @FXML
    protected HBox stateHbox;
    @FXML
    protected MFXProgressBar statusProcessBar;
    @FXML
    protected MFXButton responseContentButton;
    @FXML
    protected HBox responseContentHbox;
}
