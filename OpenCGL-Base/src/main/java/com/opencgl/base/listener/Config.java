package com.opencgl.base.listener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.opencgl.base.model.Base;
import com.opencgl.base.model.CategoryIconInfo;
import com.opencgl.base.model.OpenCGLSelfProperties;

/**
 * 全局配置管理器 (Migrated from OpenCGL_New)
 * 
 * @author Chance.W
 * @version 1.0
 * @since v2.0
 */
public class Config {
    private static final Logger logger = LoggerFactory.getLogger(Config.class);

    private static final Map<String, String> configMap = new ConcurrentHashMap<>();
    private static final File externalConfigFile = new File(Base.BASE_CONF_FILE);
    private static final String applicationPropertiesPath = "com/opencgl/config/application.properties";
    private static final Properties internalProperties = new Properties();
    private static final ConfigFileWriter configFileWriter = new ConfigFileWriter();

    static {
        // Load internal properties
        try (InputStream input = Config.class.getClassLoader().getResourceAsStream(applicationPropertiesPath)) {
            if (input == null) {
                logger.error("Sorry, unable to find " + applicationPropertiesPath);
            } else {
                internalProperties.load(input);
            }
        } catch (IOException ex) {
            logger.error("Error loading internal properties", ex);
        }

        // Initialize external config
        if (!externalConfigFile.exists()) {
            writeDefaultExternalConfig();
        } else {
            readExternalConfig();
        }
    }

    public static String get(String configKey) {
        if (configMap.containsKey(configKey)) {
            return configMap.get(configKey);
        }
        return internalProperties.getProperty(configKey);
    }

    public static String readInternalConfigure(String configKey) {
        return get(configKey);
    }

    public static String readExternalConfigure(String configKey) {
        return get(configKey);
    }

    public static synchronized void updateExternalConfigure(Map<String, String> map) {
        // 更新已有的 key 并添加新的 key
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();
            String newValue = entry.getValue();
            if (newValue != null) {
                configMap.put(key, newValue);
            }
        }
        Map<String, String> toSave = new HashMap<>(configMap);
        scheduleSave(toSave);
    }

    private static synchronized void writeDefaultExternalConfig() {
        File file = new File(Base.BASE_CONF_FILE);
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            if (!file.exists()) {
                file.createNewFile();
            }

            Map<String, String> map = new HashMap<>();
            map.put("theme", "default");
            map.put("language", "SIMPLIFIED_CHINESE");

            ConfigFileWriter.writeAtomically(file.toPath(), JSON.toJSONString(map));
            configMap.putAll(map);
        } catch (IOException e) {
            logger.error("Failed to write default config", e);
        }
    }

    private static void readExternalConfig() {
        File file = new File(Base.BASE_CONF_FILE);
        try {
            if (!file.exists()) {
                writeDefaultExternalConfig();
                return;
            }

            String jsonString = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            if (jsonString != null && !jsonString.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, String> loadedMap = JSON.parseObject(jsonString, Map.class);
                if (loadedMap != null) {
                    configMap.putAll(loadedMap);
                }
            }
        } catch (IOException e) {
            logger.error("Failed to read external config", e);
        }
    }

    /**
     * 更新单个配置项并保存
     */
    public static synchronized void updateSingleConfig(String key, String value) {
        configMap.put(key, value);
        Map<String, String> toSave = new HashMap<>(configMap);
        scheduleSave(toSave);
    }

    /**
     * 读取分类图标配置
     *
     * @return 分类图标映射
     */
    public static Map<String, CategoryIconInfo> readCategoryIcons() {
        String json = configMap.get(OpenCGLSelfProperties.CATEGORY_ICONS_KEY);
        if (json == null || json.isEmpty() || json.equals("{}")) {
            return new HashMap<>();
        }
        try {
            return JSON.parseObject(json,
                    new TypeReference<Map<String, CategoryIconInfo>>() {
                    });
        } catch (Exception e) {
            logger.warn("解析图标配置失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 保存分类图标配置
     *
     * @param icons 分类图标映射
     */
    public static void saveCategoryIcons(Map<String, CategoryIconInfo> icons) {
        configMap.put(OpenCGLSelfProperties.CATEGORY_ICONS_KEY, JSON.toJSONString(icons));
        Map<String, String> toSave = new HashMap<>(configMap);
        scheduleSave(toSave);
    }

    private static void scheduleSave(Map<String, String> values) {
        configFileWriter.submit(externalConfigFile.toPath(), JSON.toJSONString(values))
            .exceptionally(error -> {
                logger.error("Failed to persist external config", error);
                return null;
            });
    }

    /** Waits until every configuration change submitted before this call is on disk. */
    public static boolean awaitPendingWrites(Duration timeout) {
        return configFileWriter.awaitPendingWrites(timeout);
    }
}
