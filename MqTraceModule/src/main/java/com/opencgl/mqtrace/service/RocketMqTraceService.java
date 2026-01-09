package com.opencgl.mqtrace.service;

import com.alibaba.rocketmq.client.consumer.DefaultMQPushConsumer;
import com.alibaba.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import com.alibaba.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import com.alibaba.rocketmq.client.producer.DefaultMQProducer;
import com.alibaba.rocketmq.common.message.MessageExt;
import com.alibaba.rocketmq.tools.admin.DefaultMQAdminExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

/**
 * RocketMQ 消息追踪服务
 *
 * @author OpenCGL
 */
public class RocketMqTraceService implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(RocketMqTraceService.class);

    private DefaultMQAdminExt adminExt;
    private String namesrvAddr;
    private String accessKey;
    private String secretKey;

    /**
     * 连接到 RocketMQ
     */
    public void connect(String namesrvAddr, String accessKey, String secretKey) throws Exception {
        close();
        this.namesrvAddr = namesrvAddr != null ? namesrvAddr.trim() : null;
        this.accessKey = accessKey;
        this.secretKey = secretKey;

        if (this.namesrvAddr == null || this.namesrvAddr.isEmpty()) {
            throw new IllegalArgumentException("Name Server 地址不能为空");
        }

        adminExt = new DefaultMQAdminExt();
        adminExt.setNamesrvAddr(this.namesrvAddr);
        adminExt.setInstanceName("MqTraceAdmin_" + System.currentTimeMillis());

        // ACL 认证
        if (accessKey != null && !accessKey.isEmpty()) {
            System.setProperty("rocketmq.client.accessKey", accessKey);
            System.setProperty("rocketmq.client.secretKey", secretKey != null ? secretKey : "");
        }

        adminExt.start();
    }
    
    /**
     * 连接到 RocketMQ (无认证)
     */
    public void connect(String namesrvAddr) throws Exception {
        connect(namesrvAddr, null, null);
    }

    /**
     * 测试连接
     */
    public boolean testConnection() {
        if (adminExt == null) return false;
        try {
            adminExt.fetchAllTopicList();
            return true;
        } catch (Exception e) {
            logger.error("RocketMQ 连接测试失败", e);
            return false;
        }
    }

    /**
     * 获取 Topic 列表
     */
    public List<String> listTopics() throws Exception {
        if (adminExt == null) throw new IllegalStateException("未连接");
        com.alibaba.rocketmq.common.protocol.body.TopicList topicList = adminExt.fetchAllTopicList();
        List<String> sorted = new ArrayList<>(topicList.getTopicList());
        Collections.sort(sorted);
        return sorted;
    }

    /**
     * 根据 Message ID 查询消息
     */
    public MessageExt queryMessageById(String msgId) throws Exception {
        if (adminExt == null) throw new IllegalStateException("未连接");
        return adminExt.viewMessage(msgId);
    }

    /**
     * 根据 Key 查询消息
     */
    public List<MessageExt> queryMessageByKey(String topic, String key, int maxNum) throws Exception {
        if (adminExt == null) throw new IllegalStateException("未连接");
        return adminExt.queryMessage(topic, key, maxNum, 0, System.currentTimeMillis()).getMessageList();
    }

    /**
     * 获取消息轨迹（3.x 若有 messageTrackDetail 则返回，否则返回空列表）
     */
    public List<MessageTrackDto> messageTrack(MessageExt msg) throws Exception {
        if (adminExt == null) throw new IllegalStateException("未连接");
        try {
            @SuppressWarnings("unchecked")
            List<com.alibaba.rocketmq.tools.admin.api.MessageTrack> tracks = adminExt.messageTrackDetail(msg);
            if (tracks == null) return Collections.emptyList();
            List<MessageTrackDto> list = new ArrayList<>();
            for (com.alibaba.rocketmq.tools.admin.api.MessageTrack t : tracks) {
                String trackType = t.getTrackType() != null ? t.getTrackType().name() : "";
                list.add(new MessageTrackDto(t.getConsumerGroup(), trackType, t.getExceptionDesc()));
            }
            return list;
        } catch (Throwable e) {
            logger.debug("messageTrackDetail not available or failed", e);
            return Collections.emptyList();
        }
    }

    /** 消息轨迹 DTO，与 3.x MessageTrack 解耦 */
    public static class MessageTrackDto {
        private final String consumerGroup;
        private final String trackType;
        private final String exceptionDesc;
        public MessageTrackDto(String consumerGroup, String trackType, String exceptionDesc) {
            this.consumerGroup = consumerGroup;
            this.trackType = trackType;
            this.exceptionDesc = exceptionDesc;
        }
        public String getConsumerGroup() { return consumerGroup; }
        public String getTrackType() { return trackType; }
        public String getExceptionDesc() { return exceptionDesc; }
    }

    /**
     * 获取消费组列表
     */
    public Set<String> listConsumerGroups() throws Exception {
        if (adminExt == null) throw new IllegalStateException("未连接");
        // 使用 fetchAllTopicList 获取包含消费组信息的 topic
        com.alibaba.rocketmq.common.protocol.body.TopicList topicList = adminExt.fetchAllTopicList();
        Set<String> topics = new HashSet<>(topicList.getTopicList());
        Set<String> groups = new HashSet<>();
        for (String topic : topics) {
            if (topic.startsWith("%RETRY%")) {
                groups.add(topic.substring(7)); // 去掉 %RETRY% 前缀
            }
        }
        return groups;
    }

    /**
     * 查询死信队列消息
     */
    public List<MessageExt> queryDLQMessages(String consumerGroup, int maxNum) throws Exception {
        String dlqTopic = "%DLQ%" + consumerGroup;
        return queryMessageByKey(dlqTopic, "*", maxNum);
    }

    /**
     * 重发死信消息
     */
    public void resendDLQMessage(MessageExt msg, String targetTopic) throws Exception {
        DefaultMQProducer producer = new DefaultMQProducer("MqTraceResend");
        producer.setNamesrvAddr(namesrvAddr);
        try {
            producer.start();
            com.alibaba.rocketmq.common.message.Message newMsg =
                new com.alibaba.rocketmq.common.message.Message(targetTopic, msg.getBody());
            newMsg.setKeys(msg.getKeys());
            newMsg.setTags(msg.getTags());
            producer.send(newMsg);
        } finally {
            producer.shutdown();
        }
    }

    // ==================== 消息发送功能 ====================
    
    private DefaultMQProducer producer;
    
    /**
     * 发送消息
     */
    public String sendMessage(String topic, String tags, String keys, String body) throws Exception {
        if (namesrvAddr == null) throw new IllegalStateException("未连接");
        
        if (producer == null) {
            producer = new DefaultMQProducer("MqTrace_Producer_" + System.currentTimeMillis());
            producer.setNamesrvAddr(namesrvAddr);
            producer.start();
        }
        
        com.alibaba.rocketmq.common.message.Message msg =
            new com.alibaba.rocketmq.common.message.Message(topic, tags, keys, body.getBytes());

        com.alibaba.rocketmq.client.producer.SendResult result = producer.send(msg);
        return result.getMsgId();
    }

    // ==================== 消息订阅功能 ====================
    
    private DefaultMQPushConsumer consumer;
    private volatile boolean consuming = false;
    
    /**
     * 订阅消息
     */
    public void subscribe(String topic, String consumerGroup, String tags, 
                          Consumer<MessageExt> messageHandler) throws Exception {
        if (namesrvAddr == null) throw new IllegalStateException("未连接");
        
        stopConsume();
        
        consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(namesrvAddr);
        consumer.subscribe(topic, tags == null || tags.isEmpty() ? "*" : tags);
        consumer.setInstanceName("MqTrace_Consumer_" + System.currentTimeMillis());
        
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                messageHandler.accept(msg);
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        
        consuming = true;
        consumer.start();
    }
    
    /**
     * 停止消费
     */
    public void stopConsume() {
        consuming = false;
        if (consumer != null) {
            consumer.shutdown();
            consumer = null;
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
        stopConsume();
        if (producer != null) {
            producer.shutdown();
            producer = null;
        }
        if (adminExt != null) {
            adminExt.shutdown();
            adminExt = null;
        }
    }

    /**
     * 消息轨迹结果
     */
    public record TraceResult(
        String msgId,
        String topic,
        String tags,
        String keys,
        String body,
        long bornTimestamp,
        String bornHost,
        List<TrackInfo> tracks
    ) {}

    public record TrackInfo(
        String consumerGroup,
        String trackType,
        String exceptionDesc
    ) {}
}
