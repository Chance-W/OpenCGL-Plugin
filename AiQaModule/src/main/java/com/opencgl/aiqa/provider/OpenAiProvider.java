package com.opencgl.aiqa.provider;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.opencgl.aiqa.model.AiConfig;
import com.opencgl.aiqa.model.ChatMessage;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * OpenAI 兼容 Provider
 * 支持 OpenAI、Azure OpenAI、以及其他兼容 OpenAI API 的服务
 */
public class OpenAiProvider implements AiProvider {
    private static final Logger logger = LoggerFactory.getLogger(OpenAiProvider.class);
    
    private AiConfig config;
    private OkHttpClient client;
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "openai-provider");
        thread.setDaemon(true);
        return thread;
    });
    
    public OpenAiProvider() {
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }
    
    @Override
    public String getName() {
        return "OpenAI";
    }
    
    @Override
    public void configure(AiConfig config) {
        this.config = config;
    }
    
    @Override
    public CompletableFuture<String> chat(List<ChatMessage> messages) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = config.getOpenaiBaseUrl() + "/v1/chat/completions";
                
                JSONObject body = new JSONObject();
                body.put("model", config.getOpenaiModel());
                body.put("stream", false);
                
                if (config.getTemperature() != null) {
                    body.put("temperature", config.getTemperature());
                }
                if (config.getMaxTokens() != null) {
                    body.put("max_tokens", config.getMaxTokens());
                }
                
                JSONArray messagesArray = new JSONArray();
                for (ChatMessage msg : messages) {
                    JSONObject msgObj = new JSONObject();
                    msgObj.put("role", msg.getRole().name().toLowerCase());
                    msgObj.put("content", msg.getContent());
                    messagesArray.add(msgObj);
                }
                body.put("messages", messagesArray);
                
                Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                    .header("Content-Type", "application/json");
                
                if (config.getOpenaiApiKey() != null && !config.getOpenaiApiKey().isEmpty()) {
                    requestBuilder.header("Authorization", "Bearer " + config.getOpenaiApiKey());
                }
                
                try (Response response = client.newCall(requestBuilder.build()).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "";
                        throw new IOException("请求失败: " + response.code() + " - " + errorBody);
                    }
                    
                    String responseBody = response.body().string();
                    JSONObject json = JSON.parseObject(responseBody);
                    return json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");
                }
            } catch (Exception e) {
                logger.error("OpenAI 请求失败", e);
                throw new RuntimeException("OpenAI 请求失败: " + e.getMessage(), e);
            }
        }, executor);
    }
    
    @Override
    public void chatStream(List<ChatMessage> messages, 
                          Consumer<String> onToken,
                          Runnable onComplete,
                          Consumer<Throwable> onError) {
        CompletableFuture.runAsync(() -> {
            try {
                String url = config.getOpenaiBaseUrl() + "/v1/chat/completions";
                
                JSONObject body = new JSONObject();
                body.put("model", config.getOpenaiModel());
                body.put("stream", true);
                
                if (config.getTemperature() != null) {
                    body.put("temperature", config.getTemperature());
                }
                
                JSONArray messagesArray = new JSONArray();
                for (ChatMessage msg : messages) {
                    JSONObject msgObj = new JSONObject();
                    msgObj.put("role", msg.getRole().name().toLowerCase());
                    msgObj.put("content", msg.getContent());
                    messagesArray.add(msgObj);
                }
                body.put("messages", messagesArray);
                
                Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                    .header("Content-Type", "application/json");
                
                if (config.getOpenaiApiKey() != null && !config.getOpenaiApiKey().isEmpty()) {
                    requestBuilder.header("Authorization", "Bearer " + config.getOpenaiApiKey());
                }
                
                try (Response response = client.newCall(requestBuilder.build()).execute()) {
                    if (!response.isSuccessful()) {
                        onError.accept(new IOException("请求失败: " + response.code()));
                        return;
                    }
                    
                    BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()));
                    String line;
                    long lastDataTime = System.currentTimeMillis();
                    int emptyLineCount = 0;
                    
                    while ((line = reader.readLine()) != null) {
                        // 超时检测 (30秒无数据)
                        if (System.currentTimeMillis() - lastDataTime > 30000) {
                            System.out.println("[OpenAI调试] 流超时，强制结束");
                            break;
                        }
                        
                        // 空行检测
                        if (line.isEmpty()) {
                            emptyLineCount++;
                            if (emptyLineCount > 10) {
                                System.out.println("[OpenAI调试] 检测到连续空行，结束流");
                                break;
                            }
                            continue;
                        }
                        emptyLineCount = 0;
                        lastDataTime = System.currentTimeMillis();
                        
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6).trim();
                            System.out.println("[OpenAI调试] SSE 数据: " + (data.length() > 100 ? data.substring(0, 100) + "..." : data));
                            
                            if ("[DONE]".equals(data)) {
                                System.out.println("[OpenAI调试] 收到 [DONE] 信号");
                                break;
                            }
                            
                            if (data.isEmpty()) continue;
                            
                            try {
                                JSONObject json = JSON.parseObject(data);
                                JSONArray choices = json.getJSONArray("choices");
                                if (choices != null && !choices.isEmpty()) {
                                    JSONObject choice = choices.getJSONObject(0);
                                    JSONObject delta = choice.getJSONObject("delta");
                                    
                                    if (delta != null) {
                                        System.out.println("[OpenAI调试] Delta 内容: " + delta.toJSONString());
                                        
                                        // 尝试获取思考内容 (多种可能的字段名)
                                        String reasoning = delta.getString("reasoning_content");
                                        if (reasoning == null) reasoning = delta.getString("reasoning");
                                        if (reasoning == null) reasoning = delta.getString("thinking");
                                        if (reasoning == null) reasoning = delta.getString("thought");
                                        
                                        if (reasoning != null && !reasoning.isEmpty()) {
                                            System.out.println("[OpenAI调试] 收到思考内容: " + reasoning);
                                            onToken.accept("💭" + reasoning);
                                        }
                                        
                                        // 正式回复内容
                                        String content = delta.getString("content");
                                        if (content != null && !content.isEmpty()) {
                                            onToken.accept(content);
                                        }
                                    }
                                    
                                    // 检查 finish_reason
                                    Object finishReason = choice.get("finish_reason");
                                    if (finishReason != null && !"null".equals(String.valueOf(finishReason))) {
                                        System.out.println("[OpenAI调试] 流结束: finish_reason=" + finishReason);
                                        break;
                                    }
                                }
                            } catch (Exception parseEx) {
                                System.out.println("[OpenAI调试] JSON 解析异常: " + parseEx.getMessage());
                            }
                        }
                    }
                    onComplete.run();
                }
            } catch (Exception e) {
                logger.error("OpenAI 流式请求失败", e);
                onError.accept(e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Boolean> testConnection() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = config.getOpenaiBaseUrl() + "/v1/models";
                
                Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .get();
                
                if (config.getOpenaiApiKey() != null && !config.getOpenaiApiKey().isEmpty()) {
                    requestBuilder.header("Authorization", "Bearer " + config.getOpenaiApiKey());
                }
                
                try (Response response = client.newCall(requestBuilder.build()).execute()) {
                    return response.isSuccessful();
                }
            } catch (Exception e) {
                logger.error("OpenAI 连接测试失败", e);
                return false;
            }
        }, executor);
    }

    @Override
    public void close() {
        cleanup("active calls", () -> client.dispatcher().cancelAll());
        cleanup("provider executor", () -> executor.shutdownNow());
        cleanup("HTTP dispatcher", () -> client.dispatcher().executorService().shutdown());
        cleanup("HTTP connection pool", () -> client.connectionPool().evictAll());
        cleanup("HTTP cache", this::closeCache);
    }

    private void closeCache() {
        if (client.cache() == null) return;
        try {
            client.cache().close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void cleanup(String resource, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            logger.warn("Failed to close OpenAI {}", resource, e);
        }
    }
}
