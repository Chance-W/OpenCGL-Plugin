package com.opencgl.aiqa.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 配置模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConfig {
    
    public enum ProviderType {
        OLLAMA("Ollama (本地)"),
        OPENAI("OpenAI 兼容"),
        CUSTOM("自定义 API");
        
        private final String displayName;
        
        ProviderType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    private ProviderType providerType;
    
    // Ollama 配置
    private String ollamaHost;      // 默认: http://localhost:11434
    private String ollamaModel;     // 默认: llama2
    
    // OpenAI 兼容配置
    private String openaiBaseUrl;   // 默认: https://api.openai.com
    private String openaiApiKey;
    private String openaiModel;     // 默认: gpt-3.5-turbo
    
    // 自定义 API 配置
    private String customUrl;
    private String customHeaders;   // JSON 格式
    private String customBodyTemplate; // 请求体模板
    
    // 通用配置
    private Double temperature;     // 默认: 0.7
    private Integer maxTokens;      // 默认: 2048
    
    public static AiConfig defaultOllama() {
        return AiConfig.builder()
            .providerType(ProviderType.OLLAMA)
            .ollamaHost("http://localhost:11434")
            .ollamaModel("llama2")
            .temperature(0.7)
            .maxTokens(2048)
            .build();
    }
    
    public static AiConfig defaultOpenAi() {
        return AiConfig.builder()
            .providerType(ProviderType.OPENAI)
            .openaiBaseUrl("https://api.openai.com")
            .openaiModel("gpt-3.5-turbo")
            .temperature(0.7)
            .maxTokens(2048)
            .build();
    }
}
