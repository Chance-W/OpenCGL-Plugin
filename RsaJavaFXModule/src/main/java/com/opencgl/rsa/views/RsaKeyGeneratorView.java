package com.opencgl.rsa.views;

import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXCheckbox;
import io.github.palexdev.materialfx.controls.MFXComboBox;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

/**
 * RSA密钥生成器视图基类
 * 定义FXML绑定的UI组件
 * 
 * @author Chance.W
 * @date 2025-12-31
 */
public class RsaKeyGeneratorView {

    @FXML
    public StackPane rootPane;

    @FXML
    public BorderPane contentBorderPane;

    // ========== 密钥生成Tab ==========
    @FXML
    public MFXComboBox<String> keySizeCombo;

    @FXML
    public MFXComboBox<String> algorithmCombo;

    @FXML
    public MFXComboBox<String> hashAlgorithmCombo;

    @FXML
    public MFXButton generateButton;

    @FXML
    public MFXCheckbox removePemHeaderCheckbox;

    @FXML
    public TextArea publicKeyArea;

    @FXML
    public TextArea privateKeyArea;

    @FXML
    public MFXButton savePublicKeyButton;

    @FXML
    public MFXButton savePrivateKeyButton;

    @FXML
    public MFXButton copyPublicKeyButton;

    @FXML
    public MFXButton copyPrivateKeyButton;

    // ========== 加密解密Tab ==========
    @FXML
    public TextArea encryptPublicKeyArea;

    @FXML
    public TextArea encryptPrivateKeyArea;

    @FXML
    public TextArea plaintextArea;

    @FXML
    public TextArea ciphertextArea;

    @FXML
    public MFXButton encryptButton;

    @FXML
    public MFXButton decryptButton;

    // ========== 签名验签Tab ==========
    @FXML
    public TextArea signPrivateKeyArea;

    @FXML
    public TextArea signPublicKeyArea;

    @FXML
    public TextArea signDataArea;

    @FXML
    public TextArea signatureArea;

    @FXML
    public MFXButton signButton;

    @FXML
    public MFXButton verifyButton;

    @FXML
    public TextField verifyResultField;

    // ========== AES加解密Tab ==========
    @FXML
    public TextField aesKeyField;

    @FXML
    public TextField aesIvField;

    @FXML
    public TextArea aesPlaintextArea;

    @FXML
    public TextArea aesCiphertextArea;

    @FXML
    public MFXButton aesEncryptButton;

    @FXML
    public MFXButton aesDecryptButton;

    // ========== License管理器Tab - JSON生成 ==========
    @FXML
    public TextField jsonEmailField;

    @FXML
    public TextField jsonJobNumberField;

    @FXML
    public TextField jsonNameField;

    @FXML
    public TextField jsonEffectiveDaysField;

    @FXML
    public TextField jsonExpiryDateField;

    @FXML
    public TextField jsonIssueDateField;

    @FXML
    public TextField jsonUsePurposeField;

    @FXML
    public TextField jsonProjectCodeField;

    @FXML
    public MFXButton generateJsonButton;

    @FXML
    public MFXButton copyJsonButton;

    @FXML
    public TextArea jsonOutputArea;

    // ========== License管理器Tab - 签名验签 ==========
    @FXML
    public TextArea licensePrivateKeyArea;

    @FXML
    public TextArea licensePublicKeyArea;

    @FXML
    public MFXButton licenseSignButton;

    @FXML
    public MFXButton licenseVerifyButton;

    @FXML
    public MFXButton appendSignatureButton;

    @FXML
    public TextArea licenseSignatureArea;

    @FXML
    public TextField licenseVerifyResultField;

    // ========== License管理器Tab - AES加解密 ==========
    @FXML
    public TextField licenseAesKeyField;

    @FXML
    public TextField licenseAesIvField;

    @FXML
    public MFXButton licenseAesEncryptButton;

    @FXML
    public MFXButton licenseAesDecryptButton;

    @FXML
    public MFXButton copyEncryptedButton;

    @FXML
    public TextArea licenseEncryptedArea;
}
