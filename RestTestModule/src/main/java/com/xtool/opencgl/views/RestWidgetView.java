package com.xtool.opencgl.views;


import com.opencgl.base.controls.CustomTextArea;
import com.xtool.opencgl.model.DataAttribute;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXComboBox;
import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.fxml.FXML;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class RestWidgetView {
    @FXML
    protected MFXButton headersAddButton;
    @FXML
    protected MFXButton headersDelButton;
    @FXML
    protected TreeTableView<DataAttribute> headers;
    @FXML
    protected TreeTableColumn<DataAttribute, String> headersKey;
    @FXML
    protected TreeTableColumn<DataAttribute, String> headersValue;
    @FXML
    protected TreeTableColumn<DataAttribute, String> headersDescription;

    @FXML
    protected MFXButton cookiesAddButton;
    @FXML
    protected MFXButton cookiesDelButton;
    @FXML
    protected TreeTableView<DataAttribute> cookies;
    @FXML
    protected TreeTableColumn<DataAttribute, String> cookiesKey;
    @FXML
    protected TreeTableColumn<DataAttribute, String> cookiesValue;
    @FXML
    protected TreeTableColumn<DataAttribute, String> cookiesDescription;
    @FXML
    protected StackPane mainStackPane;
    @FXML
    protected BorderPane contentBorderPane;
    @FXML
    protected SplitPane splitPane;
    @FXML
    protected StackPane contentInputAndOutputPane;
    @FXML
    protected MFXButton sendButton;
    @FXML
    protected MFXButton refreshButton;
    @FXML
    protected MFXButton saveButton;
    @FXML
    protected MFXButton copyButton;
    @FXML
    protected MFXTextField urlTextField;
    @FXML
    protected TabPane restJfxTabPane;
    @FXML
    protected MFXComboBox<String> chooseMetComboBox;
    @FXML
    protected CustomTextArea outputTextArea;
    @FXML
    protected CustomTextArea inputTextArea;
    @FXML
    protected VBox operateTopVbox;
    @FXML
    protected MFXComboBox<String> chooseMediaTypeComboBox;


}
