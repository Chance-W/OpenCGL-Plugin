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
 * Ollama Provider
 * 支持本地部署的 Ollama (Llama, Mistral 等模型)
 */
public class OllamaProvider implements AiProvider {
    private static final Logger logger = LoggerFactory.getLogger(OllamaProvider.class);
    
    private AiConfig config;
    private OkHttpClient client;
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "ollama-provider");
        thread.setDaemon(true);
        return thread;
    });
    
    public OllamaProvider() {
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }
    
    @Override
    public String getName() {
        return "Ollama";
    }
    
    @Override
    public void configure(AiConfig config) {
        this.config = config;
    }
    
    @Override
    public CompletableFuture<String> chat(List<ChatMessage> messages) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = config.getOllamaHost() + "/api/chat";
                
                JSONObject body = new JSONObject();
                body.put("model", config.getOllamaModel());
                body.put("stream", false);
                
                JSONArray messagesArray = new JSONArray();
                for (ChatMessage msg : messages) {
                    JSONObject msgObj = new JSONObject();
                    msgObj.put("role", msg.getRole().name().toLowerCase());
                    msgObj.put("content", msg.getContent());
                    messagesArray.add(msgObj);
                }
                body.put("messages", messagesArray);
                
                if (config.getTemperature() != null) {
                    JSONObject options = new JSONObject();
                    options.put("temperature", config.getTemperature());
                    body.put("options", options);
                }
                
                Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                    .build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("请求失败: " + response.code());
                    }
                    
                    String responseBody = response.body().string();
                    JSONObject json = JSON.parseObject(responseBody);
                    return json.getJSONObject("message").getString("content");
                }
            } catch (Exception e) {
                logger.error("Ollama 请求失败", e);
                throw new RuntimeException("Ollama 请求失败: " + e.getMessage(), e);
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
                String url = config.getOllamaHost() + "/api/chat";
                
                JSONObject body = new JSONObject();
                body.put("model", config.getOllamaModel());
                body.put("stream", true);
                
                JSONArray messagesArray = new JSONArray();
                for (ChatMessage msg : messages) {
                    JSONObject msgObj = new JSONObject();
                    msgObj.put("role", msg.getRole().name().toLowerCase());
                    msgObj.put("content", msg.getContent());
                    messagesArray.add(msgObj);
                }
                body.put("messages", messagesArray);
                
                Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                    .build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "";
                        onError.accept(new IOException("请求失败: " + response.code() + " - " + errorBody));
                        return;
                    }
                    
                    BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()));
                    String line;
                    int emptyLineCount = 0; // 连续空行计数，防止死循环
                    long lastDataTime = System.currentTimeMillis();
                    
                    while ((line = reader.readLine()) != null) {
                        // 检测超时 (30秒无数据则退出)
                        if (System.currentTimeMillis() - lastDataTime > 30000) {
                            logger.warn("流超时，强制结束");
                            break;
                        }
                        
                        if (line.isEmpty()) {
                            emptyLineCount++;
                            // 连续 10 个空行则认为结束
                            if (emptyLineCount > 10) {
                                logger.info("检测到连续空行，结束流");
                                break;
                            }
                            continue;
                        }
                        
                        emptyLineCount = 0;
                        lastDataTime = System.currentTimeMillis();
                        
                        try {
                            String jsonStr = line;
                            
                            // 处理 SSE 格式 (OpenAI 兼容 / llama.cpp)
                            if (line.startsWith("data: ")) {
                                jsonStr = line.substring(6).trim();
                                System.out.println("[AI调试] SSE 格式数据: " + (jsonStr.length() > 100 ? jsonStr.substring(0, 100) + "..." : jsonStr));
                                if ("[DONE]".equals(jsonStr)) {
                                    System.out.println("[AI调试] 收到 [DONE] 信号");
                                    break;
                                }
                            }
                            
                            // 跳过空 JSON
                            if (jsonStr.isEmpty()) continue;
                            
                            JSONObject json = JSON.parseObject(jsonStr);
                            System.out.println("[AI调试] JSON 包含的键: " + json.keySet());
                            
                            // 尝试 Ollama 原生格式
                            if (json.containsKey("message")) {
                                System.out.println("[AI调试] 进入 Ollama 原生格式分支");
                                String content = json.getJSONObject("message").getString("content");
                                if (content != null && !content.isEmpty()) {
                                    onToken.accept(content);
                                }
                                if (json.getBooleanValue("done")) {
                                    break;
                                }
                            }
                            // 尝试 OpenAI 兼容格式 (llama.cpp)
                            else if (json.containsKey("choices")) {
                                System.out.println("[AI调试] 进入 OpenAI 兼容格式分支");
                                JSONArray choices = json.getJSONArray("choices");
                                if (choices != null && !choices.isEmpty()) {
                                    JSONObject choice = choices.getJSONObject(0);
                                    JSONObject delta = choice.getJSONObject("delta");
                                    if (delta != null) {
                                        System.out.println("[AI调试] Delta 内容: " + delta.toJSONString());
                                        
                                        // 尝试多个可能的思考字段名
                                        String reasoning = delta.getString("reasoning_content");
                                        if (reasoning == null) reasoning = delta.getString("reasoning");
                                        if (reasoning == null) reasoning = delta.getString("thinking");
                                        if (reasoning == null) reasoning = delta.getString("thought");
                                        
                                        if (reasoning != null && !reasoning.isEmpty()) {
                                            System.out.println("[AI调试] 收到思考内容: " + reasoning);
                                            onToken.accept("💭" + reasoning);
                                        }
                                        
                                        // 显示正式回复 (content)
                                        String content = delta.getString("content");
                                        if (content != null && !content.isEmpty()) {
                                            onToken.accept(content);
                                        }
                                    }
                                    // 检查结束标志
                                    Object finishReason = choice.get("finish_reason");
                                    if (finishReason != null && !"null".equals(String.valueOf(finishReason))) {
                                        System.out.println("[AI调试] 流结束: finish_reason=" + finishReason);
                                        break;
                                    }
                                }
                            }
                        } catch (Exception parseEx) {
                            System.out.println("[AI调试] 跳过无法解析的行: " + line);
                        }
                    }
                    onComplete.run();
                }
            } catch (Exception e) {
                logger.error("Ollama 流式请求失败", e);
                onError.accept(e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Boolean> testConnection() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = config.getOllamaHost() + "/api/tags";
                Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();
                
                try (Response response = client.newCall(request).execute()) {
                    return response.isSuccessful();
                }
            } catch (Exception e) {
                logger.error("Ollama 连接测试失败", e);
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
            logger.warn("Failed to close Ollama {}", resource, e);
        }
    }
}
