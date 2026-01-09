package com.opencgl.aiqa.model;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型配置 - 保存的模型信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfig {
    
    private String id;          // 唯一标识
    private String name;        // 显示名称 (如 "DeepSeek R1")
    private String baseUrl;     // API 地址
    private String apiKey;      // API Key
    private String model;       // 模型名称
    private Double temperature; // 温度
    
    /**
     * 创建默认配置
     */
    public static ModelConfig createDefault(String name) {
        return ModelConfig.builder()
            .id(UUID.randomUUID().toString())
            .name(name)
            .baseUrl("http://localhost:8181")
            .apiKey("")
            .model("deepseek-r1")
            .temperature(0.7)
            .build();
    }
    
    @Override
    public String toString() {
        return name;
    }
}
