package com.opencgl.mqtrace.service;

import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * RabbitMQ 消息追踪服务
 *
 * @author OpenCGL
 */
public class RabbitMqTraceService implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMqTraceService.class);

    private Connection connection;
    private Channel channel;
    private volatile boolean tracing = false;
    private final Queue<TracedMessage> tracedMessages = new ConcurrentLinkedQueue<>();

    /**
     * 连接到 RabbitMQ
     */
    public void connect(String host, int port, String username, String password, String virtualHost) 
            throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);
        factory.setVirtualHost(virtualHost);
        
        connection = factory.newConnection();
        channel = connection.createChannel();
    }

    /**
     * 测试连接
     */
    public boolean testConnection() {
        return connection != null && connection.isOpen();
    }

    /**
     * 获取队列列表（需要 Management API）
     */
    public List<String> listQueues() throws IOException {
        if (channel == null) throw new IllegalStateException("未连接");
        // RabbitMQ AMQP 协议不直接支持列出所有队列
        // 这里返回一个示例，实际需要使用 Management HTTP API
        return List.of("请通过 Management API 查看队列列表");
    }

    /**
     * 获取队列消息数量
     */
    public long getQueueMessageCount(String queueName) throws IOException {
        if (channel == null) throw new IllegalStateException("未连接");
        AMQP.Queue.DeclareOk declareOk = channel.queueDeclarePassive(queueName);
        return declareOk.getMessageCount();
    }

    /**
     * 消费队列消息（不确认）
     */
    public List<TracedMessage> peekMessages(String queueName, int maxCount) throws IOException {
        if (channel == null) throw new IllegalStateException("未连接");
        
        List<TracedMessage> messages = new ArrayList<>();
        for (int i = 0; i < maxCount; i++) {
            GetResponse response = channel.basicGet(queueName, false);
            if (response == null) break;
            
            AMQP.BasicProperties props = response.getProps();
            String body = new String(response.getBody(), StandardCharsets.UTF_8);
            
            messages.add(new TracedMessage(
                props.getMessageId(),
                response.getEnvelope().getRoutingKey(),
                response.getEnvelope().getExchange(),
                queueName,
                body,
                props.getTimestamp() != null ? props.getTimestamp().getTime() : System.currentTimeMillis(),
                props.getHeaders() != null ? formatHeaders(props.getHeaders()) : "",
                response.getEnvelope().getDeliveryTag()
            ));
            
            // 重新入队（不消费）
            channel.basicNack(response.getEnvelope().getDeliveryTag(), false, true);
        }
        return messages;
    }

    /**
     * 启用消息追踪（使用 Firehose）
     */
    public void startTracing(String firehoseQueue, Consumer<TracedMessage> messageHandler) throws IOException {
        if (channel == null) throw new IllegalStateException("未连接");
        
        // 绑定到 Firehose 交换机（需要先启用 rabbitmq_tracing 插件）
        channel.queueDeclare(firehoseQueue, false, false, true, null);
        channel.queueBind(firehoseQueue, "amq.rabbitmq.trace", "#");
        
        tracing = true;
        channel.basicConsume(firehoseQueue, true, new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, 
                                       AMQP.BasicProperties properties, byte[] body) {
                if (!tracing) return;
                
                TracedMessage msg = new TracedMessage(
                    properties.getMessageId(),
                    envelope.getRoutingKey(),
                    envelope.getExchange(),
                    firehoseQueue,
                    new String(body, StandardCharsets.UTF_8),
                    System.currentTimeMillis(),
                    formatHeaders(properties.getHeaders()),
                    envelope.getDeliveryTag()
                );
                tracedMessages.offer(msg);
                messageHandler.accept(msg);
            }
        });
    }

    /**
     * 停止追踪
     */
    public void stopTracing() {
        tracing = false;
    }

    /**
     * 获取追踪到的消息
     */
    public List<TracedMessage> getTracedMessages() {
        return new ArrayList<>(tracedMessages);
    }

    /**
     * 清空追踪消息
     */
    public void clearTracedMessages() {
        tracedMessages.clear();
    }

    /**
     * 查看死信队列
     */
    public List<TracedMessage> peekDeadLetterQueue(String dlxQueue, int maxCount) throws IOException {
        return peekMessages(dlxQueue, maxCount);
    }

    /**
     * 重发死信消息
     */
    public void resendMessage(String targetExchange, String routingKey, String body) throws IOException {
        if (channel == null) throw new IllegalStateException("未连接");
        channel.basicPublish(targetExchange, routingKey, null, body.getBytes(StandardCharsets.UTF_8));
    }

    private String formatHeaders(Map<String, Object> headers) {
        if (headers == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }

    // ==================== 消息发送功能 ====================
    
    /**
     * 发送消息
     */
    public void sendMessage(String exchange, String routingKey, String body, Map<String, Object> headers) 
            throws IOException {
        if (channel == null) throw new IllegalStateException("未连接");
        
        AMQP.BasicProperties.Builder propsBuilder = new AMQP.BasicProperties.Builder()
            .messageId(UUID.randomUUID().toString())
            .timestamp(new Date());
        
        if (headers != null && !headers.isEmpty()) {
            propsBuilder.headers(headers);
        }
        
        channel.basicPublish(exchange, routingKey, propsBuilder.build(), body.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * 发送消息到队列（使用默认交换机）
     */
    public void sendToQueue(String queueName, String body) throws IOException {
        sendMessage("", queueName, body, null);
    }

    // ==================== 消息订阅功能 ====================
    
    private volatile boolean consuming = false;
    private String currentConsumerTag;
    
    /**
     * 订阅队列消息
     */
    public void subscribe(String queueName, Consumer<TracedMessage> messageHandler) throws IOException {
        if (channel == null) throw new IllegalStateException("未连接");
        
        stopConsume();
        
        // 确保队列存在
        channel.queueDeclare(queueName, true, false, false, null);
        
        consuming = true;
        currentConsumerTag = channel.basicConsume(queueName, false, new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope,
                                       AMQP.BasicProperties properties, byte[] body) throws IOException {
                if (!consuming) return;
                
                TracedMessage msg = new TracedMessage(
                    properties.getMessageId(),
                    envelope.getRoutingKey(),
                    envelope.getExchange(),
                    queueName,
                    new String(body, StandardCharsets.UTF_8),
                    properties.getTimestamp() != null ? properties.getTimestamp().getTime() : System.currentTimeMillis(),
                    formatHeaders(properties.getHeaders()),
                    envelope.getDeliveryTag()
                );
                
                messageHandler.accept(msg);
                
                // 确认消息
                channel.basicAck(envelope.getDeliveryTag(), false);
            }
        });
    }
    
    /**
     * 停止消费
     */
    public void stopConsume() {
        consuming = false;
        if (currentConsumerTag != null && channel != null) {
            try {
                channel.basicCancel(currentConsumerTag);
            } catch (IOException e) {
                logger.error("取消消费失败", e);
            }
            currentConsumerTag = null;
        }
    }
    
    /**
     * 是否正在消费
     */
    public boolean isConsuming() {
        return consuming;
    }

    @Override
    public void close() {
        tracing = false;
        stopConsume();
        try {
            if (channel != null) {
                channel.close();
                channel = null;
            }
            if (connection != null) {
                connection.close();
                connection = null;
            }
        } catch (Exception e) {
            logger.error("关闭 RabbitMQ 连接失败", e);
        }
    }

    public record TracedMessage(
        String messageId,
        String routingKey,
        String exchange,
        String queue,
        String body,
        long timestamp,
        String headers,
        long deliveryTag
    ) {}
}
