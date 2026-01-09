package com.opencgl.aiqa.provider;

import com.opencgl.aiqa.model.AiConfig;
import com.opencgl.aiqa.model.ChatMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * AI Provider 接口
 * 定义与 AI 系统交互的标准接口
 */
public interface AiProvider extends AutoCloseable {
    
    /**
     * 获取 Provider 名称
     */
    String getName();
    
    /**
     * 配置 Provider
     */
    void configure(AiConfig config);
    
    /**
     * 发送聊天请求（非流式）
     * @param messages 消息列表
     * @return 异步响应
     */
    CompletableFuture<String> chat(List<ChatMessage> messages);
    
    /**
     * 发送聊天请求（流式）
     * @param messages 消息列表
     * @param onToken 每收到一个 token 时的回调
     * @param onComplete 完成时的回调
     * @param onError 错误时的回调
     */
    void chatStream(List<ChatMessage> messages, 
                    Consumer<String> onToken,
                    Runnable onComplete,
                    Consumer<Throwable> onError);
    
    /**
     * 测试连接
     * @return 是否连接成功
     */
    CompletableFuture<Boolean> testConnection();

    @Override
    void close();
}
