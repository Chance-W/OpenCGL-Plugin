package com.opencgl.http.service;

import com.alibaba.fastjson.JSON;
import com.opencgl.http.model.ProxyConfig;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 代理配置服务
 * 负责保存和读取代理设置
 */
public class ProxyConfigService {
    private static final Logger logger = LoggerFactory.getLogger(ProxyConfigService.class);
    private static final String CONFIG_FILE = System.getProperty("user.home") + "/.opencgl/proxy_config.json";
    
    private ProxyConfig currentConfig;

    public ProxyConfigService() {
        loadConfig();
    }

    public ProxyConfig getConfig() {
        return currentConfig;
    }

    public void saveConfig(ProxyConfig config) {
        this.currentConfig = config;
        try {
            File file = new File(CONFIG_FILE);
            FileUtils.write(file, JSON.toJSONString(config, true), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Failed to save proxy config", e);
        }
    }

    private void loadConfig() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try {
                String content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
                currentConfig = JSON.parseObject(content, ProxyConfig.class);
            } catch (IOException e) {
                logger.error("Failed to load proxy config", e);
                currentConfig = new ProxyConfig();
            }
        } else {
            currentConfig = new ProxyConfig();
        }
    }
}
