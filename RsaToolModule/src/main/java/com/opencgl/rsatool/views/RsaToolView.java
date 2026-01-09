package com.opencgl.rsatool.views;

import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXCheckbox;
import io.github.palexdev.materialfx.controls.MFXComboBox;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

/**
 * RSA工具视图
 */
public class RsaToolView {

    @FXML public StackPane rootPane;
    @FXML public BorderPane contentBorderPane;

    // 密钥生成
    @FXML public MFXComboBox<String> keySizeCombo;
    @FXML public MFXComboBox<String> algorithmCombo;
    @FXML public MFXComboBox<String> hashAlgorithmCombo;
    @FXML public MFXButton generateButton;
    @FXML public MFXCheckbox removePemHeaderCheckbox;
    @FXML public TextArea publicKeyArea;
    @FXML public TextArea privateKeyArea;
    @FXML public MFXButton savePublicKeyButton;
    @FXML public MFXButton savePrivateKeyButton;
    @FXML public MFXButton copyPublicKeyButton;
    @FXML public MFXButton copyPrivateKeyButton;

    // 加密解密
    @FXML public TextArea encryptPublicKeyArea;
    @FXML public TextArea encryptPrivateKeyArea;
    @FXML public TextArea plaintextArea;
    @FXML public TextArea ciphertextArea;
    @FXML public MFXButton encryptButton;
    @FXML public MFXButton decryptButton;

    // 签名验签
    @FXML public TextArea signPrivateKeyArea;
    @FXML public TextArea signPublicKeyArea;
    @FXML public TextArea signDataArea;
    @FXML public TextArea signatureArea;
    @FXML public MFXButton signButton;
    @FXML public MFXButton verifyButton;
    @FXML public TextField verifyResultField;
}
