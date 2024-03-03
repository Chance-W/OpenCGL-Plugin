package com.xtool.opencgl.views;


import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.layout.AnchorPane;


/**
 * @author Chance.W
 */
public class RedisWidgetView {
    @FXML
    protected AnchorPane mainAnchorPane;
    @FXML
    protected MFXTextField clusterRedisAdress;
    @FXML
    protected MFXButton connectButton;
    @FXML
    protected MFXButton disconnectButton;
    @FXML
    protected MFXTextField keypattern;
    @FXML
    protected MFXButton queryKeyValueButton;
    @FXML
    protected MFXButton deleteKeyButton;
    @FXML
    protected MFXButton queryPatternKeyButton;
    @FXML
    protected MFXTextField setKey;
    @FXML
    protected MFXTextField setValue;
    @FXML
    protected MFXButton setKeyAndValueButton;
    @FXML
    protected TextArea resultDetail;
    @FXML
    protected MFXButton hsetButton;
    @FXML
    protected MFXButton hgetButton;
    @FXML
    protected MFXButton hdelButton;
    @FXML
    protected MFXTextField hashKey;
    @FXML
    protected MFXTextField hashMapKey;
    @FXML
    protected MFXTextField hashMapValue;
}
