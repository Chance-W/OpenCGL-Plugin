package com.opencgl.nginx.controller;

import com.opencgl.nginx.i18n.I18N;
import com.opencgl.nginx.model.NginxConfig;
import com.opencgl.nginx.model.NginxConfig.*;
import com.opencgl.nginx.service.NginxConfigGenerator;
import com.opencgl.nginx.views.NginxConfigView;
import javafx.fxml.Initializable;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Nginx 配置生成器控制器
 */
public class NginxConfigController extends NginxConfigView implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(NginxConfigController.class);
    
    private final NginxConfigGenerator generator = new NginxConfigGenerator();
    private final NginxConfig config = new NginxConfig();
    
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupLoadBalanceComboBox();
        setupToggleBindings();
        bindEvents();
        initI18n();
        setStatus(I18N.get("status.ready"));
        
        // 生成初始配置
        generateConfig();
    }

    private void initI18n() {
    }
    
    private void setupLoadBalanceComboBox() {
        loadBalanceComboBox.getItems().addAll(
            I18N.get("loadBalance.roundRobin"),
            I18N.get("loadBalance.weight"),
            I18N.get("loadBalance.ipHash"),
            I18N.get("loadBalance.leastConn")
        );
        loadBalanceComboBox.selectFirst();
    }
    
    private void setupToggleBindings() {
        // SSL 切换
        sslToggle.selectedProperty().addListener((obs, old, newVal) -> {
            sslConfigBox.setDisable(!newVal);
            if (newVal) {
                listenPortField.setText("443");
            } else {
                listenPortField.setText("80");
            }
        });
        
        // 代理切换
        proxyToggle.selectedProperty().addListener((obs, old, newVal) -> {
            proxyConfigBox.setDisable(!newVal);
        });
        
        // 负载均衡切换
        upstreamToggle.selectedProperty().addListener((obs, old, newVal) -> {
            upstreamConfigBox.setDisable(!newVal);
        });
    }
    
    private void bindEvents() {
        generateButton.setOnAction(e -> generateConfig());
        copyButton.setOnAction(e -> copyConfig());
        saveButton.setOnAction(e -> saveConfig());
        
        // 实时预览
        serverNameField.textProperty().addListener((obs, old, newVal) -> generateConfig());
        listenPortField.textProperty().addListener((obs, old, newVal) -> generateConfig());
        rootPathField.textProperty().addListener((obs, old, newVal) -> generateConfig());
        indexField.textProperty().addListener((obs, old, newVal) -> generateConfig());
        sslCertField.textProperty().addListener((obs, old, newVal) -> generateConfig());
        sslKeyField.textProperty().addListener((obs, old, newVal) -> generateConfig());
        proxyLocationField.textProperty().addListener((obs, old, newVal) -> generateConfig());
        proxyPassField.textProperty().addListener((obs, old, newVal) -> generateConfig());
        upstreamNameField.textProperty().addListener((obs, old, newVal) -> generateConfig());
        upstreamServersArea.textProperty().addListener((obs, old, newVal) -> generateConfig());
        loadBalanceComboBox.valueProperty().addListener((obs, old, newVal) -> generateConfig());
        sslToggle.selectedProperty().addListener((obs, old, newVal) -> generateConfig());
        proxyToggle.selectedProperty().addListener((obs, old, newVal) -> generateConfig());
        upstreamToggle.selectedProperty().addListener((obs, old, newVal) -> generateConfig());
    }
    
    private void generateConfig() {
        try {
            // 读取配置
            config.setServerName(serverNameField.getText().trim());
            config.setListenPort(parsePort(listenPortField.getText()));
            config.setRoot(rootPathField.getText().trim());
            config.setIndex(indexField.getText().trim());
            
            config.setSslEnabled(sslToggle.isSelected());
            config.setSslCertificate(sslCertField.getText().trim());
            config.setSslCertificateKey(sslKeyField.getText().trim());
            
            config.setProxyEnabled(proxyToggle.isSelected());
            config.setProxyLocation(proxyLocationField.getText().trim());
            config.setProxyPass(proxyPassField.getText().trim());
            
            config.setUpstreamEnabled(upstreamToggle.isSelected());
            config.setUpstreamName(upstreamNameField.getText().trim());
            config.setLoadBalanceType(getSelectedLoadBalanceType());
            config.setUpstreamServers(parseUpstreamServers());
            
            // 生成配置
            String result = generator.generate(config);
            previewArea.setText(result);
            setStatus(I18N.get("status.generated"));
        } catch (Exception e) {
            setStatus(I18N.get("status.genFailed", e.getMessage()));
        }
    }
    
    private int parsePort(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 80;
        }
    }
    
    private LoadBalanceType getSelectedLoadBalanceType() {
        String selected = loadBalanceComboBox.getValue();
        String[] keys = {"loadBalance.roundRobin", "loadBalance.weight", "loadBalance.ipHash", "loadBalance.leastConn"};
        LoadBalanceType[] types = LoadBalanceType.values();
        for (int i = 0; i < types.length && i < keys.length; i++) {
            if (I18N.get(keys[i]).equals(selected)) {
                return types[i];
            }
        }
        return LoadBalanceType.ROUND_ROBIN;
    }
    
    private List<UpstreamServer> parseUpstreamServers() {
        List<UpstreamServer> servers = new ArrayList<>();
        String text = upstreamServersArea.getText();
        if (text == null || text.isEmpty()) return servers;
        
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            String[] parts = line.split("\\s+");
            String address = parts[0];
            int weight = 1;
            if (parts.length > 1) {
                try {
                    weight = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {}
            }
            servers.add(new UpstreamServer(address, weight));
        }
        return servers;
    }
    
    private void copyConfig() {
        String content = previewArea.getText();
        if (!content.isEmpty()) {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(content);
            Clipboard.getSystemClipboard().setContent(cc);
            setStatus(I18N.get("status.copied"));
        }
    }
    
    private void saveConfig() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18N.get("dialog.saveTitle"));
        chooser.setInitialFileName("nginx.conf");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter(I18N.get("filter.nginxConf"), "*.conf")
        );
        File file = chooser.showSaveDialog(mainStackPane.getScene().getWindow());
        
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(previewArea.getText());
                setStatus(I18N.get("status.saved", file.getName()));
            } catch (Exception e) {
                setStatus(I18N.get("status.saveFailed", e.getMessage()));
            }
        }
    }
    
    private void setStatus(String status) {
        statusLabel.setText(status);
    }
}
