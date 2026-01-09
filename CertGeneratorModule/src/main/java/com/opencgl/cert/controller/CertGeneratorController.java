package com.opencgl.cert.controller;

import com.opencgl.cert.service.CertificateService;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXCheckbox;
import io.github.palexdev.materialfx.controls.MFXComboBox;
import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import com.opencgl.cert.i18n.I18N;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class CertGeneratorController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(CertGeneratorController.class);
    private final CertificateService certificateService = new CertificateService();
    private final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "certificate-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final List<Future<?>> backgroundTasks = new CopyOnWriteArrayList<>();
    private volatile boolean disposed;

    @FXML
    private javafx.scene.control.Tab generatorTab;
    @FXML
    private javafx.scene.control.Tab verifierTab;

    // Subject Fields
    @FXML
    private MFXTextField commonNameField;
    @FXML
    private MFXTextField orgField;
    @FXML
    private MFXTextField orgUnitField;
    @FXML
    private MFXTextField localityField;
    @FXML
    private MFXTextField stateField;
    @FXML
    private MFXTextField countryField;

    // Key Config
    @FXML
    private MFXComboBox<String> keyAlgoCombo;
    @FXML
    private MFXComboBox<Integer> keySizeCombo;
    @FXML
    private MFXTextField validityField;

    // Extension
    @FXML
    private TextArea sanArea;

    // Output
    @FXML
    private MFXCheckbox outputJksCheck;
    @FXML
    private MFXCheckbox outputP12Check;
    @FXML
    private MFXCheckbox outputPemCheck;
    @FXML
    private MFXTextField keystorePassField;
    @FXML
    private MFXTextField aliasField;

    @FXML
    private MFXButton generateButton;
    @FXML
    private Label statusLabel;

    // Verifier
    @FXML
    private MFXTextField verifyFilePathField;
    @FXML
    private MFXButton verifyBrowseBtn;
    @FXML
    private MFXTextField verifyPasswordField;
    @FXML
    private MFXButton verifyButton;
    @FXML
    private TextArea verifyResultArea;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initI18n();
        initGeneratorUI();
        initVerifierUI();
    }

    private void initI18n() {
        generatorTab.textProperty().bind(I18N.getBinding("tab.generator"));
        verifierTab.textProperty().bind(I18N.getBinding("tab.verifier"));

        commonNameField.floatingTextProperty().bind(I18N.getBinding("label.cn"));
        orgField.floatingTextProperty().bind(I18N.getBinding("label.org"));
        orgUnitField.floatingTextProperty().bind(I18N.getBinding("label.unit"));
        localityField.floatingTextProperty().bind(I18N.getBinding("label.city"));
        stateField.floatingTextProperty().bind(I18N.getBinding("label.state"));
        countryField.floatingTextProperty().bind(I18N.getBinding("label.country"));

        keyAlgoCombo.floatingTextProperty().bind(I18N.getBinding("label.algo"));
        keySizeCombo.floatingTextProperty().bind(I18N.getBinding("label.size"));
        validityField.floatingTextProperty().bind(I18N.getBinding("label.validity"));

        outputJksCheck.textProperty().bind(I18N.getBinding("check.jks"));
        outputP12Check.textProperty().bind(I18N.getBinding("check.p12"));
        outputPemCheck.textProperty().bind(I18N.getBinding("check.pem"));

        keystorePassField.floatingTextProperty().bind(I18N.getBinding("label.password"));
        aliasField.floatingTextProperty().bind(I18N.getBinding("label.alias"));

        generateButton.textProperty().bind(I18N.getBinding("btn.generate"));

        verifyFilePathField.floatingTextProperty().bind(I18N.getBinding("label.file_path"));
        verifyBrowseBtn.textProperty().bind(I18N.getBinding("btn.browse"));
        verifyPasswordField.floatingTextProperty().bind(I18N.getBinding("label.password"));
        verifyButton.textProperty().bind(I18N.getBinding("btn.verify"));

        sanArea.promptTextProperty().bind(I18N.getBinding("prompt.san"));
    }

    private void initGeneratorUI() {
        keyAlgoCombo.getItems().addAll("RSA", "EC");
        keyAlgoCombo.selectFirst();

        keySizeCombo.getItems().addAll(2048, 4096);
        keySizeCombo.selectFirst();

        // Dynamic Key Size based on Algo
        keyAlgoCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            keySizeCombo.getSelectionModel().clearSelection(); // Prevent MFX Invalid Range error
            if ("EC".equals(newVal)) {
                keySizeCombo.getItems().setAll(256);
            } else {
                keySizeCombo.getItems().setAll(2048, 4096);
            }
            keySizeCombo.selectFirst();
        });

        generateButton.setOnAction(e -> generateCertificate());
    }

    private void initVerifierUI() {
        verifyBrowseBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Certificates/Keystores", "*.jks", "*.p12",
                    "*.pem", "*.cer", "*.crt"));
            File file = fc.showOpenDialog(verifyBrowseBtn.getScene().getWindow());
            if (file != null) {
                verifyFilePathField.setText(file.getAbsolutePath());
            }
        });

        verifyButton.setOnAction(e -> verifyCertificate());
    }

    private void generateCertificate() {
        try {
            statusLabel.setText(I18N.get("msg.generating"));
            statusLabel.setStyle("-fx-text-fill: orange;");

            // 1. Gather Input
            String cn = commonNameField.getText();
            if (cn == null || cn.isEmpty()) {
                throw new IllegalArgumentException(I18N.get("msg.required", I18N.get("label.cn")));
            }

            StringBuilder dnBuilder = new StringBuilder("CN=").append(cn);
            if (!orgField.getText().isEmpty())
                dnBuilder.append(", O=").append(orgField.getText());
            if (!orgUnitField.getText().isEmpty())
                dnBuilder.append(", OU=").append(orgUnitField.getText());
            if (!localityField.getText().isEmpty())
                dnBuilder.append(", L=").append(localityField.getText());
            if (!stateField.getText().isEmpty())
                dnBuilder.append(", ST=").append(stateField.getText());
            if (!countryField.getText().isEmpty())
                dnBuilder.append(", C=").append(countryField.getText());
            String subjectDn = dnBuilder.toString();

            int days = Integer.parseInt(validityField.getText());
            String algo = keyAlgoCombo.getValue();
            int keySize = keySizeCombo.getValue();

            List<String> sansList = new ArrayList<>();
            if (sanArea.getText() != null && !sanArea.getText().trim().isEmpty()) {
                sansList = Arrays.stream(sanArea.getText().split("\n"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
            }
            final List<String> sans = sansList;

            // 2. Select Output Directory
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle(I18N.get("section.output"));
            File outDir = dc.showDialog(generateButton.getScene().getWindow());
            if (outDir == null) {
                statusLabel.setText(I18N.get("msg.cancelled"));
                statusLabel.setStyle("-fx-text-fill: gray;");
                return;
            }

            // 3. Generate Logic (Async)
            submitBackground(() -> {
                try {
                    // Generate
                    KeyPair kp = certificateService.generateKeyPair(algo, keySize);
                    X509Certificate cert = certificateService.generateSelfSignedCert(kp, subjectDn, days, sans, true); // Root
                                                                                                                       // CA
                                                                                                                       // mode
                                                                                                                       // for
                                                                                                                       // now

                    String password = keystorePassField.getText();
                    String alias = aliasField.getText();
                    String baseName = cn.replaceAll("[^a-zA-Z0-9.-]", "_");

                    // Save
                    if (outputJksCheck.isSelected()) {
                        File jksFile = new File(outDir, baseName + ".jks");
                        certificateService.saveToKeystore(cert, kp.getPrivate(), alias, password, jksFile, "JKS");
                    }
                    if (outputP12Check.isSelected()) {
                        File p12File = new File(outDir, baseName + ".p12");
                        certificateService.saveToKeystore(cert, kp.getPrivate(), alias, password, p12File, "PKCS12");
                    }
                    if (outputPemCheck.isSelected()) {
                        certificateService.saveCertToPem(cert, new File(outDir, baseName + ".crt"));
                        certificateService.saveKeyToPem(kp.getPrivate(), new File(outDir, baseName + ".key"));
                    }

                    Platform.runLater(() -> {
                        if (disposed) return;
                        statusLabel.setText(I18N.get("msg.success", outDir.getName()));
                        statusLabel.setStyle("-fx-text-fill: green;");
                    });

                } catch (Exception ex) {
                    logger.error("Generation failed", ex);
                    Platform.runLater(() -> {
                        if (disposed) return;
                        statusLabel.setText(I18N.get("msg.error", ex.getMessage()));
                        statusLabel.setStyle("-fx-text-fill: red;");
                        ex.printStackTrace(); // For console
                    });
                }
            });

        } catch (Exception e) {
            statusLabel.setText(I18N.get("msg.error", e.getMessage()));
            statusLabel.setStyle("-fx-text-fill: red;");
        }
    }

    private void verifyCertificate() {
        verifyResultArea.setText(I18N.get("msg.verifying"));
        String path = verifyFilePathField.getText();
        if (path == null || path.isEmpty()) {
            verifyResultArea.setText(I18N.get("msg.select_file"));
            return;
        }

        submitBackground(() -> {
            try {
                File file = new File(path);
                StringBuilder report = new StringBuilder();
                report.append(I18N.get("label.file")).append(file.getName()).append("\n");

                if (path.endsWith(".pem") || path.endsWith(".crt") || path.endsWith(".cer")) {
                    X509Certificate cert = certificateService.loadCertificate(file);
                    report.append(I18N.get("msg.type_x509")).append("\n");
                    report.append(I18N.get("msg.subject", cert.getSubjectX500Principal())).append("\n");
                    report.append(I18N.get("msg.issuer", cert.getIssuerX500Principal())).append("\n");
                    report.append(I18N.get("msg.serial", cert.getSerialNumber())).append("\n");
                    report.append(I18N.get("msg.valid_from", cert.getNotBefore())).append("\n");
                    report.append(I18N.get("msg.valid_until", cert.getNotAfter())).append("\n");

                    try {
                        certificateService.verifyCertificate(cert, null); // Self-signed check
                        report.append(I18N.get("msg.status_ok")).append("\n");
                    } catch (Exception e) {
                        report.append(I18N.get("msg.status_fail", e.getMessage())).append("\n");
                    }
                } else {
                    // Keystore Logic
                    String password = verifyPasswordField.getText();
                    String type = path.toLowerCase().endsWith(".p12") || path.toLowerCase().endsWith(".pfx") ? "PKCS12"
                            : "JKS";

                    if (password == null || password.isEmpty()) {
                        report.append(I18N.get("msg.keystore_attempt", type)).append("\n");
                    }

                    KeyStore ks = certificateService.loadKeystore(file, password, type);
                    report.append(I18N.get("msg.type_keystore", type)).append("\n");
                    report.append(I18N.get("msg.aliases")).append("\n");

                    java.util.Enumeration<String> aliases = ks.aliases();
                    while (aliases.hasMoreElements()) {
                        String alias = aliases.nextElement();
                        report.append(I18N.get("msg.alias", alias)).append("\n");

                        if (ks.isKeyEntry(alias)) {
                            report.append(I18N.get("msg.type_key")).append("\n");
                        } else if (ks.isCertificateEntry(alias)) {
                            report.append(I18N.get("msg.type_cert")).append("\n");
                        }
                        Certificate cert = ks.getCertificate(alias);
                        if (cert instanceof X509Certificate) {
                            X509Certificate x509 = (X509Certificate) cert;
                            report.append(I18N.get("msg.subject", x509.getSubjectX500Principal())).append("\n");
                            report.append(I18N.get("msg.issuer", x509.getIssuerX500Principal())).append("\n");
                            report.append(I18N.get("msg.valid_until", x509.getNotAfter())).append("\n");
                        }
                        report.append("\n");
                    }
                }

                Platform.runLater(() -> {
                    if (!disposed) verifyResultArea.setText(report.toString());
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!disposed) verifyResultArea.setText(I18N.get("msg.error_read", e.getMessage()));
                });
            }
        });
    }

    private void submitBackground(Runnable operation) {
        if (disposed) return;
        backgroundTasks.removeIf(Future::isDone);
        backgroundTasks.add(backgroundExecutor.submit(operation));
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        for (Future<?> task : backgroundTasks) {
            try {
                task.cancel(true);
            } catch (RuntimeException e) {
                logger.warn("Failed to cancel certificate task", e);
            }
        }
        backgroundTasks.clear();
        backgroundExecutor.shutdownNow();
    }
}
