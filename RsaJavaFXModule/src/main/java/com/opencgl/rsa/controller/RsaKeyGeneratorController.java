package com.opencgl.rsa.controller;

import com.opencgl.rsa.i18n.I18N;
import com.opencgl.rsa.service.RsaKeyService;
import com.opencgl.rsa.views.RsaKeyGeneratorView;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * RSA密钥生成器控制器
 * 
 * @author Chance.W
 * @date 2025-12-31
 */
public class RsaKeyGeneratorController extends RsaKeyGeneratorView implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(RsaKeyGeneratorController.class);
    
    private final RsaKeyService rsaKeyService = new RsaKeyService();
    private String currentPublicKey = "";
    private String currentPrivateKey = "";
    private ProgressIndicator loadingIndicator;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "rsa-key-generator");
        thread.setDaemon(true);
        return thread;
    });
    private Future<?> keyGenerationFuture;
    private volatile boolean disposed;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initComponents();
        bindEvents();
        setupLoadingIndicator();
        initI18n();
    }

    private void initI18n() {
    }

    /**
     * 初始化UI组件
     */
    private void initComponents() {
        // 初始化密钥长度选项
        keySizeCombo.setItems(FXCollections.observableArrayList(
            "1024位（不推荐，安全性低）",
            "2048位（推荐）",
            "4096位（高安全）",
            "8192位（极高安全，生成慢）"
        ));
        keySizeCombo.selectFirst();

        // 初始化算法类型选项
        algorithmCombo.setItems(FXCollections.observableArrayList(
            "RSA-OAEP（加密/解密）",
            "RSASSA-PKCS1-v1_5（签名/验证）"
        ));
        algorithmCombo.selectFirst();

        // 初始化哈希算法选项
        hashAlgorithmCombo.setItems(FXCollections.observableArrayList(
            "SHA-1（兼容性）",
            "SHA-256（推荐）",
            "SHA-384（更高安全）",
            "SHA-512（最高安全）"
        ));
        hashAlgorithmCombo.selectIndex(1); // 默认选择SHA-256

        // 设置文本框为可编辑（允许用户粘贴密钥）
        publicKeyArea.setEditable(true);
        privateKeyArea.setEditable(true);
        
        // 设置字体为等宽字体
        publicKeyArea.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 12px;");
        privateKeyArea.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 12px;");

        // 所有按钮默认启用，无需生成密钥后才能使用
        logger.info("UI组件初始化完成");
    }

    /**
     * 设置加载指示器
     */
    private void setupLoadingIndicator() {
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxSize(100, 100);
        loadingIndicator.setVisible(false);
        rootPane.getChildren().add(loadingIndicator);
        StackPane.setAlignment(loadingIndicator, javafx.geometry.Pos.CENTER);
    }

    /**
     * 绑定事件处理器
     */
    private void bindEvents() {
        // 生成密钥按钮事件
        generateButton.setOnAction(event -> generateKeyPair());

        // 保存公钥按钮事件
        savePublicKeyButton.setOnAction(event -> saveKey("PUBLIC", currentPublicKey));

        // 保存私钥按钮事件
        savePrivateKeyButton.setOnAction(event -> saveKey("PRIVATE", currentPrivateKey));

        // 复制公钥按钮事件
        copyPublicKeyButton.setOnAction(event -> copyToClipboard(currentPublicKey, "公钥"));

        // 复制私钥按钮事件
        copyPrivateKeyButton.setOnAction(event -> copyToClipboard(currentPrivateKey, I18N.get("name.privateKey")));

        // 加密按钮事件
        encryptButton.setOnAction(event -> performEncrypt());

        // 解密按钮事件
        decryptButton.setOnAction(event -> performDecrypt());

        // 签名按钮事件
        signButton.setOnAction(event -> performSign());

        // 验签按钮事件
        verifyButton.setOnAction(event -> performVerify());
        
        // AES加密按钮事件
        if (aesEncryptButton != null) {
            aesEncryptButton.setOnAction(event -> performAesEncrypt());
        }
        
        // AES解密按钮事件
        if (aesDecryptButton != null) {
            aesDecryptButton.setOnAction(event -> performAesDecrypt());
        }
        
        // JSON生成按钮事件
        if (generateJsonButton != null) {
            generateJsonButton.setOnAction(event -> generateJson());
        }
        
        // JSON复制按钮事件
        if (copyJsonButton != null) {
            copyJsonButton.setOnAction(event -> copyToClipboard(jsonOutputArea.getText(), I18N.get("name.json")));
        }
        
        // ===== License管理器事件 =====
        // License签名按钮
        if (licenseSignButton != null) {
            licenseSignButton.setOnAction(event -> performLicenseSign());
        }
        
        // License验签按钮
        if (licenseVerifyButton != null) {
            licenseVerifyButton.setOnAction(event -> performLicenseVerify());
        }
        
        // 追加signature字段按钮
        if (appendSignatureButton != null) {
            appendSignatureButton.setOnAction(event -> appendSignatureToJson());
        }
        
        // License AES加密按钮
        if (licenseAesEncryptButton != null) {
            licenseAesEncryptButton.setOnAction(event -> performLicenseAesEncrypt());
        }
        
        // License AES解密按钮
        if (licenseAesDecryptButton != null) {
            licenseAesDecryptButton.setOnAction(event -> performLicenseAesDecrypt());
        }
        
        // 复制密文按钮
        if (copyEncryptedButton != null) {
            copyEncryptedButton.setOnAction(event -> copyToClipboard(licenseEncryptedArea.getText(), I18N.get("name.ciphertext")));
        }
    }

    /**
     * 生成RSA密钥对
     */
    private void generateKeyPair() {
        int keySize = getSelectedKeySize();
        showLoading(true);

        keyGenerationFuture = backgroundExecutor.submit(() -> {
            try {
                logger.info("开始生成{}位RSA密钥对", keySize);
                
                Map<String, String> keyPair = rsaKeyService.generateKeyPair(keySize, 65537);
                
                currentPublicKey = keyPair.get("publicKey");
                currentPrivateKey = keyPair.get("privateKey");

                Platform.runLater(() -> {
                    if (disposed) return;
                    showLoading(false);
                    
                    // 检查是否需要移除PEM头尾
                    boolean removePemHeader = removePemHeaderCheckbox != null && removePemHeaderCheckbox.isSelected();
                    
                    if (removePemHeader) {
                        publicKeyArea.setText(removePemHeaders(currentPublicKey));
                        privateKeyArea.setText(removePemHeaders(currentPrivateKey));
                    } else {
                        publicKeyArea.setText(currentPublicKey);
                        privateKeyArea.setText(currentPrivateKey);
                    }
                    
                    showInfo(I18N.get("msg.keyGenSuccess"));
                    logger.info("RSA密钥对生成成功");
                });

            } catch (Exception e) {
                logger.error("生成密钥失败", e);
                Platform.runLater(() -> {
                    if (disposed) return;
                    showLoading(false);
                    showError(I18N.get("msg.keyGenFailed", e.getMessage()));
                });
            }
        });
    }

    /**
     * 移除PEM格式的头尾，只保留Base64内容
     */
    private String removePemHeaders(String pemContent) {
        if (pemContent == null) return "";
        return pemContent
            .replaceAll("-----BEGIN [A-Z ]+-----", "")
            .replaceAll("-----END [A-Z ]+-----", "")
            .replaceAll("\\s+", "");
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        if (keyGenerationFuture != null) keyGenerationFuture.cancel(true);
        backgroundExecutor.shutdownNow();
    }

    /**
     * 规范化密钥输入（支持PEM格式或纯Base64）
     */
    private String normalizeKey(String key, String keyType) {
        if (key == null || key.trim().isEmpty()) return null;
        key = key.trim();
        
        // 如果已经是PEM格式，直接返回
        if (key.contains("-----BEGIN")) {
            return key;
        }
        
        // 纯Base64，添加PEM头尾
        String header, footer;
        if ("PUBLIC".equals(keyType)) {
            header = "-----BEGIN PUBLIC KEY-----";
            footer = "-----END PUBLIC KEY-----";
        } else {
            header = "-----BEGIN PRIVATE KEY-----";
            footer = "-----END PRIVATE KEY-----";
        }
        
        // 每64字符换行
        StringBuilder sb = new StringBuilder();
        sb.append(header).append("\n");
        String base64 = key.replaceAll("\\s+", "");
        for (int i = 0; i < base64.length(); i += 64) {
            sb.append(base64, i, Math.min(i + 64, base64.length())).append("\n");
        }
        sb.append(footer);
        return sb.toString();
    }

    /**
     * 显示/隐藏加载动画
     */
    private void showLoading(boolean show) {
        loadingIndicator.setVisible(show);
        generateButton.setDisable(show);
        contentBorderPane.setDisable(show);
    }

    /**
     * 获取选择的密钥长度
     */
    private int getSelectedKeySize() {
        String selected = keySizeCombo.getSelectedItem();
        if (selected == null) {
            return 2048; // 默认2048位
        }
        
        if (selected.contains("1024")) return 1024;
        if (selected.contains("2048")) return 2048;
        if (selected.contains("4096")) return 4096;
        if (selected.contains("8192")) return 8192;
        
        return 2048;
    }

    /**
     * 保存密钥到文件
     * 
     * @param keyType 密钥类型（PUBLIC/PRIVATE）
     * @param keyContent 密钥内容
     */
    private void saveKey(String keyType, String keyContent) {
        if (keyContent == null || keyContent.isEmpty()) {
            showWarning(I18N.get("msg.pleaseGenFirst"));
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("PUBLIC".equals(keyType) ? I18N.get("title.savePublic") : I18N.get("title.savePrivate"));
        fileChooser.setInitialFileName(
            "PUBLIC".equals(keyType) ? "rsa_public_key.pem" : "rsa_private_key.pem"
        );
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter(I18N.get("filter.pem"), "*.pem")
        );
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter(I18N.get("filter.all"), "*.*")
        );

        File file = fileChooser.showSaveDialog(rootPane.getScene().getWindow());
        if (file != null) {
            try {
                rsaKeyService.saveKeyToFile(keyContent, file.getAbsolutePath());
                showInfo(I18N.get("msg.keySaved"));
                logger.info("密钥已保存到：{}", file.getAbsolutePath());
            } catch (Exception e) {
                logger.error("保存密钥失败", e);
                showError(I18N.get("msg.saveFailed", e.getMessage()));
            }
        }
    }

    /**
     * 复制到剪贴板
     * 
     * @param content 要复制的内容
     * @param name 内容名称
     */
    private void copyToClipboard(String content, String name) {
        if (content == null || content.isEmpty()) {
            showWarning(I18N.get("msg.noContentToCopy"));
            return;
        }

        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putString(content);
        clipboard.setContent(clipboardContent);
        
        showInfo(I18N.get("msg.copiedToClipboard", name));
        logger.info("{}已复制到剪贴板", name);
    }

    /**
     * 显示成功Toast提示（顶部滑入，3秒后消失）
     */
    private void showInfo(String message) {
        showToast(message, "#4CAF50"); // 绿色
    }
    
    /**
     * 显示Toast通知
     */
    private void showToast(String message, String backgroundColor) {
        javafx.scene.control.Label toastLabel = new javafx.scene.control.Label("✅ " + message);
        toastLabel.setStyle(
            "-fx-background-color: " + backgroundColor + ";" +
            "-fx-text-fill: white;" +
            "-fx-padding: 12px 24px;" +
            "-fx-background-radius: 8px;" +
            "-fx-font-size: 14px;" +
            "-fx-font-weight: bold;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 3);"
        );
        
        // 添加到根面板顶部
        javafx.geometry.Pos alignment = javafx.geometry.Pos.TOP_CENTER;
        StackPane.setAlignment(toastLabel, alignment);
        StackPane.setMargin(toastLabel, new javafx.geometry.Insets(20, 0, 0, 0));
        
        // 初始透明度为0
        toastLabel.setOpacity(0);
        rootPane.getChildren().add(toastLabel);
        
        // 淡入动画
        javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(
            javafx.util.Duration.millis(300), toastLabel
        );
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        
        // 淡出动画
        javafx.animation.FadeTransition fadeOut = new javafx.animation.FadeTransition(
            javafx.util.Duration.millis(300), toastLabel
        );
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> rootPane.getChildren().remove(toastLabel));
        
        // 延迟后淡出
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(
            javafx.util.Duration.seconds(3)
        );
        pause.setOnFinished(e -> fadeOut.play());
        
        // 播放动画
        fadeIn.setOnFinished(e -> pause.play());
        fadeIn.play();
    }

    /**
     * 显示警告提示
     */
    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(I18N.get("title.warning"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(I18N.get("title.error"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 执行加密操作
     */
    private void performEncrypt() {
        String plaintext = plaintextArea.getText();
        if (plaintext == null || plaintext.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseInputPlain"));
            return;
        }

        String publicKeyInput = encryptPublicKeyArea.getText();
        if (publicKeyInput == null || publicKeyInput.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseInputPublic"));
            return;
        }
        
        String publicKey = normalizeKey(publicKeyInput, "PUBLIC");

        try {
            logger.info("开始加密数据");
            String ciphertext = rsaKeyService.encrypt(plaintext, publicKey);
            ciphertextArea.setText(ciphertext);
            showInfo(I18N.get("msg.encryptSuccess"));
            logger.info("数据加密成功");
        } catch (Exception e) {
            logger.error("加密失败", e);
            showError(I18N.get("msg.encryptFailed", e.getMessage()));
        }
    }

    private void performDecrypt() {
        String ciphertext = ciphertextArea.getText();
        if (ciphertext == null || ciphertext.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseInputCipher"));
            return;
        }

        String privateKeyInput = encryptPrivateKeyArea.getText();
        if (privateKeyInput == null || privateKeyInput.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseInputPrivate"));
            return;
        }
        
        String privateKey = normalizeKey(privateKeyInput, "PRIVATE");

        try {
            logger.info("开始解密数据");
            String plaintext = rsaKeyService.decrypt(ciphertext.trim(), privateKey);
            plaintextArea.setText(plaintext);
            showInfo(I18N.get("msg.decryptSuccess"));
            logger.info("数据解密成功");
        } catch (Exception e) {
            logger.error("解密失败", e);
            showError(I18N.get("msg.decryptFailed", e.getMessage()));
        }
    }

    private void performSign() {
        String data = signDataArea.getText();
        if (data == null || data.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseInputData"));
            return;
        }

        String privateKeyInput = signPrivateKeyArea.getText();
        if (privateKeyInput == null || privateKeyInput.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseInputPrivate"));
            return;
        }
        
        String privateKey = normalizeKey(privateKeyInput, "PRIVATE");

        try {
            logger.info("开始对数据签名");
            String signature = rsaKeyService.sign(data, privateKey);
            signatureArea.setText(signature);
            showInfo(I18N.get("msg.signSuccess"));
            logger.info("数据签名成功");
        } catch (Exception e) {
            logger.error("签名失败", e);
            showError(I18N.get("msg.signFailed", e.getMessage()));
        }
    }

    private void performVerify() {
        String data = signDataArea.getText();
        String signature = signatureArea.getText();

        if (data == null || data.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseInputDataVerify"));
            return;
        }

        if (signature == null || signature.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseInputSignature"));
            return;
        }

        String publicKeyInput = signPublicKeyArea.getText();
        if (publicKeyInput == null || publicKeyInput.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseInputPublic"));
            return;
        }
        
        String publicKey = normalizeKey(publicKeyInput, "PUBLIC");

        try {
            logger.info("开始验证签名");
            boolean isValid = rsaKeyService.verify(data, signature.trim(), publicKey);
            
            if (isValid) {
                verifyResultField.setText(I18N.get("result.verifyOk"));
                verifyResultField.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                showInfo(I18N.get("msg.verifySuccess"));
            } else {
                verifyResultField.setText(I18N.get("result.verifyFail"));
                verifyResultField.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                showWarning(I18N.get("msg.verifyFailed"));
            }
            logger.info("签名验证完成，结果：{}", isValid);
        } catch (Exception e) {
            logger.error("验签失败", e);
            verifyResultField.setText(I18N.get("result.verifyError"));
            verifyResultField.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
            showError(I18N.get("msg.signFailed", e.getMessage()));
        }
    }
    
    /**
     * 执行AES加密
     */
    private void performAesEncrypt() {
        String plaintext = aesPlaintextArea.getText();
        if (plaintext == null || plaintext.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseInputAesPlain"));
            return;
        }
        
        String key = aesKeyField.getText();
        if (key == null || key.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseInputAesKey"));
            return;
        }
        
        String iv = aesIvField.getText();
        
        try {
            logger.info("开始AES加密");
            String ciphertext = rsaKeyService.aesEncrypt(plaintext, key, iv);
            aesCiphertextArea.setText(ciphertext);
            showInfo(I18N.get("msg.aesEncryptSuccess"));
            logger.info("AES加密成功");
        } catch (Exception e) {
            logger.error("AES加密失败", e);
            showError(I18N.get("msg.aesEncryptFailed", e.getMessage()));
        }
    }
    
    private void performAesDecrypt() {
        String ciphertext = aesCiphertextArea.getText();
        if (ciphertext == null || ciphertext.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseInputAesCipher"));
            return;
        }
        
        String key = aesKeyField.getText();
        if (key == null || key.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseInputAesKey"));
            return;
        }
        
        String iv = aesIvField.getText();
        
        try {
            logger.info("开始AES解密");
            String plaintext = rsaKeyService.aesDecrypt(ciphertext.trim(), key, iv);
            aesPlaintextArea.setText(plaintext);
            showInfo(I18N.get("msg.aesDecryptSuccess"));
            logger.info("AES解密成功");
        } catch (Exception e) {
            logger.error("AES解密失败", e);
            showError(I18N.get("msg.aesDecryptFailed", e.getMessage()));
        }
    }
    
    /**
     * 生成JSON（按ASCII排序的无换行无Tab格式）
     */
    private void generateJson() {
        try {
            // 使用TreeMap自动按key的ASCII排序
            java.util.TreeMap<String, Object> map = new java.util.TreeMap<>();
            
            // 获取所有字段值
            String email = jsonEmailField.getText();
            String jobNumber = jsonJobNumberField.getText();
            String name = jsonNameField.getText();
            String effectiveDaysStr = jsonEffectiveDaysField.getText();
            String expiryDate = jsonExpiryDateField.getText();
            String issueDate = jsonIssueDateField.getText();
            String usePurpose = jsonUsePurposeField.getText();
            String projectCode = jsonProjectCodeField.getText();
            
            // 添加到TreeMap（空值也添加）
            map.put("licenseApplicantEmail", email != null ? email : "");
            map.put("licenseApplicantJobNumber", jobNumber != null ? jobNumber : "");
            map.put("licenseApplicantName", name != null ? name : "");
            
            // 有效天数转为数字
            int effectiveDays = 0;
            if (effectiveDaysStr != null && !effectiveDaysStr.trim().isEmpty()) {
                try {
                    effectiveDays = Integer.parseInt(effectiveDaysStr.trim());
                } catch (NumberFormatException e) {
                    showWarning(I18N.get("msg.effectiveDaysMustBeNumber"));
                    return;
                }
            }
            map.put("licenseEffectiveDays", effectiveDays);
            
            map.put("licenseExpiryDate", expiryDate != null ? expiryDate : "");
            map.put("licenseIssueDate", issueDate != null ? issueDate : "");
            map.put("licenseUsePurpose", usePurpose != null ? usePurpose : "");
            map.put("projectCode", projectCode != null ? projectCode : "");
            
            // 手动构建JSON字符串（无换行无Tab）
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (java.util.Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) {
                    json.append(",");
                }
                first = false;
                json.append("\"").append(entry.getKey()).append("\":");
                Object value = entry.getValue();
                if (value instanceof Number) {
                    json.append(value);
                } else {
                    json.append("\"").append(escapeJsonString(value.toString())).append("\"");
                }
            }
            json.append("}");
            
            jsonOutputArea.setText(json.toString());
            showInfo(I18N.get("msg.jsonGenSuccess"));
            logger.info("JSON生成成功");
        } catch (Exception e) {
            logger.error("JSON生成失败", e);
            showError(I18N.get("msg.jsonGenFailed", e.getMessage()));
        }
    }
    
    /**
     * 转义JSON字符串
     */
    private String escapeJsonString(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    // ===== License管理器方法 =====
    
    /**
     * License签名 - 对JSON输出进行签名
     */
    private void performLicenseSign() {
        String jsonData = jsonOutputArea.getText();
        if (jsonData == null || jsonData.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseGenJsonFirst"));
            return;
        }
        
        String privateKeyInput = licensePrivateKeyArea.getText();
        if (privateKeyInput == null || privateKeyInput.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseInputPrivate"));
            return;
        }
        
        String privateKey = normalizeKey(privateKeyInput, "PRIVATE");
        
        try {
            logger.info("开始对License JSON签名");
            String signature = rsaKeyService.sign(jsonData.trim(), privateKey);
            licenseSignatureArea.setText(signature);
            showInfo(I18N.get("msg.signSuccess"));
            logger.info("License签名成功");
        } catch (Exception e) {
            logger.error("License签名失败", e);
            showError(I18N.get("msg.signFailed", e.getMessage()));
        }
    }
    
    private void performLicenseVerify() {
        String jsonData = jsonOutputArea.getText();
        if (jsonData == null || jsonData.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseGenJsonFirst"));
            return;
        }
        
        String signature = licenseSignatureArea.getText();
        if (signature == null || signature.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseInputSignature"));
            return;
        }
        
        String publicKeyInput = licensePublicKeyArea.getText();
        if (publicKeyInput == null || publicKeyInput.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseInputPublic"));
            return;
        }
        
        String publicKey = normalizeKey(publicKeyInput, "PUBLIC");
        
        try {
            logger.info("开始验证License签名");
            boolean isValid = rsaKeyService.verify(jsonData.trim(), signature.trim(), publicKey);
            
            if (isValid) {
                licenseVerifyResultField.setText(I18N.get("result.verifyOk"));
                licenseVerifyResultField.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                showInfo(I18N.get("msg.verifySuccess"));
            } else {
                licenseVerifyResultField.setText(I18N.get("result.verifyFail"));
                licenseVerifyResultField.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                showWarning(I18N.get("msg.verifyFailed"));
            }
            logger.info("License验签完成，结果：{}", isValid);
        } catch (Exception e) {
            logger.error("License验签失败", e);
            licenseVerifyResultField.setText(I18N.get("result.verifyError"));
            licenseVerifyResultField.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
            showError(I18N.get("msg.signFailed", e.getMessage()));
        }
    }
    
    private void appendSignatureToJson() {
        String jsonData = jsonOutputArea.getText();
        if (jsonData == null || jsonData.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseGenJsonFirst"));
            return;
        }
        
        String signature = licenseSignatureArea.getText();
        if (signature == null || signature.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseSignFirst"));
            return;
        }
        
        try {
            String trimmedJson = jsonData.trim();
            if (trimmedJson.endsWith("}")) {
                if (trimmedJson.contains("\"signature\"")) {
                    showWarning(I18N.get("msg.jsonHasSignature"));
                    return;
                }
                String newJson = trimmedJson.substring(0, trimmedJson.length() - 1) 
                    + ",\"signature\":\"" + signature.trim() + "\"}";
                jsonOutputArea.setText(newJson);
                showInfo(I18N.get("msg.signatureAppended"));
                logger.info("signature字段追加成功");
            } else {
                showWarning(I18N.get("msg.invalidJson"));
            }
        } catch (Exception e) {
            logger.error("追加signature失败", e);
            showError(I18N.get("msg.appendSignatureFailed", e.getMessage()));
        }
    }
    
    /**
     * License AES加密 - 加密JSON输出
     */
    private void performLicenseAesEncrypt() {
        String jsonData = jsonOutputArea.getText();
        if (jsonData == null || jsonData.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseGenJsonFirst"));
            return;
        }
        
        String key = licenseAesKeyField.getText();
        if (key == null || key.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseInputAesKey"));
            return;
        }
        
        String iv = licenseAesIvField.getText();
        
        try {
            logger.info("开始AES加密License JSON");
            String encrypted = rsaKeyService.aesEncrypt(jsonData.trim(), key, iv);
            licenseEncryptedArea.setText(encrypted);
            showInfo(I18N.get("msg.aesEncryptSuccess"));
            logger.info("License AES加密成功");
        } catch (Exception e) {
            logger.error("License AES加密失败", e);
            showError(I18N.get("msg.aesEncryptFailed", e.getMessage()));
        }
    }
    
    private void performLicenseAesDecrypt() {
        String encrypted = licenseEncryptedArea.getText();
        if (encrypted == null || encrypted.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseInputEncrypted"));
            return;
        }
        
        String key = licenseAesKeyField.getText();
        if (key == null || key.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseInputAesKey"));
            return;
        }
        
        String iv = licenseAesIvField.getText();
        
        try {
            logger.info("开始AES解密");
            String decrypted = rsaKeyService.aesDecrypt(encrypted.trim(), key, iv);
            jsonOutputArea.setText(decrypted);
            showInfo(I18N.get("msg.aesDecryptSuccess"));
            logger.info("License AES解密成功");
        } catch (Exception e) {
            logger.error("License AES解密失败", e);
            showError(I18N.get("msg.aesDecryptFailed", e.getMessage()));
        }
    }
}
