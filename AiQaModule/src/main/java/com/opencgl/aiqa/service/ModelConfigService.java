package com.opencgl.aiqa.service;

import com.opencgl.aiqa.dao.ModelConfigDao;
import com.opencgl.aiqa.model.ModelConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型配置管理服务 - 使用 SQLite 存储
 */
public class ModelConfigService {
    private static final Logger logger = LoggerFactory.getLogger(ModelConfigService.class);
    
    private final ModelConfigDao dao;
    private List<ModelConfig> configs = new ArrayList<>();
    
    public ModelConfigService() {
        this.dao = new ModelConfigDao();
        loadConfigs();
    }
    
    /**
     * 加载配置
     */
    private void loadConfigs() {
        configs = dao.queryAll();
        logger.info("从 SQLite 加载 {} 个模型配置", configs.size());
    }
    
    /**
     * 获取所有配置
     */
    public List<ModelConfig> getConfigs() {
        return new ArrayList<>(configs);
    }
    
    /**
     * 添加配置
     */
    public void addConfig(ModelConfig config) {
        dao.insertData(config);
        configs.add(config);
    }
    
    /**
     * 更新配置
     */
    public void updateConfig(ModelConfig config) {
        dao.updateData(config);
        for (int i = 0; i < configs.size(); i++) {
            if (configs.get(i).getId().equals(config.getId())) {
                configs.set(i, config);
                break;
            }
        }
    }
    
    /**
     * 删除配置
     */
    public void deleteConfig(String id) {
        dao.deleteData(id);
        configs.removeIf(c -> c.getId().equals(id));
    }
    
    /**
     * 根据 ID 获取配置
     */
    public ModelConfig getConfigById(String id) {
        return configs.stream()
            .filter(c -> c.getId().equals(id))
            .findFirst()
            .orElse(null);
    }
}
