package com.opencgl.kafka.service;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Kafka 服务
 * 支持连接、生产、消费、Topic 管理
 *
 * @author OpenCGL
 */
public class KafkaService implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(KafkaService.class);

    private Properties connectionProps;
    private AdminClient adminClient;
    private KafkaProducer<String, String> producer;
    private KafkaConsumer<String, String> consumer;
    private Thread consumerThread;
    private volatile boolean consuming = false;

    /**
     * 连接到 Kafka 集群
     */
    public void connect(String bootstrapServers) {
        close();
        connectionProps = new Properties();
        connectionProps.put("bootstrap.servers", bootstrapServers);
        connectionProps.put("request.timeout.ms", "5000");
        connectionProps.put("default.api.timeout.ms", "5000");

        // 创建 AdminClient
        adminClient = AdminClient.create(connectionProps);
    }

    /**
     * 测试连接
     */
    public boolean testConnection() {
        if (adminClient == null) return false;
        try {
            adminClient.listTopics().names().get(5, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            logger.error("连接测试失败", e);
            return false;
        }
    }

    /**
     * 获取 Topic 列表
     */
    public List<String> listTopics() throws ExecutionException, InterruptedException, TimeoutException {
        if (adminClient == null) throw new IllegalStateException("未连接");
        Set<String> topics = adminClient.listTopics().names().get(10, TimeUnit.SECONDS);
        List<String> sorted = new ArrayList<>(topics);
        Collections.sort(sorted);
        return sorted;
    }

    /**
     * 获取 Topic 详情
     */
    public TopicDescription describeTopic(String topicName) throws ExecutionException, InterruptedException {
        if (adminClient == null) throw new IllegalStateException("未连接");
        Map<String, TopicDescription> result = adminClient.describeTopics(Collections.singletonList(topicName)).allTopicNames().get();
        return result.get(topicName);
    }

    /**
     * 创建 Topic
     */
    public void createTopic(String topicName, int partitions, short replicationFactor) 
            throws ExecutionException, InterruptedException {
        if (adminClient == null) throw new IllegalStateException("未连接");
        NewTopic newTopic = new NewTopic(topicName, partitions, replicationFactor);
        adminClient.createTopics(Collections.singletonList(newTopic)).all().get();
    }

    /**
     * 删除 Topic
     */
    public void deleteTopic(String topicName) throws ExecutionException, InterruptedException {
        if (adminClient == null) throw new IllegalStateException("未连接");
        adminClient.deleteTopics(Collections.singletonList(topicName)).all().get();
    }

    /**
     * 发送消息
     */
    public void sendMessage(String topic, String key, String value, Map<String, String> headers) 
            throws ExecutionException, InterruptedException {
        if (producer == null) {
            Properties props = new Properties();
            props.putAll(connectionProps);
            props.put("key.serializer", StringSerializer.class.getName());
            props.put("value.serializer", StringSerializer.class.getName());
            producer = new KafkaProducer<>(props);
        }

        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        if (headers != null) {
            headers.forEach((k, v) -> record.headers().add(k, v.getBytes()));
        }

        producer.send(record).get();
        producer.flush();
    }

    /**
     * 消费消息
     */
    public void consumeMessages(String topic, String groupId, Consumer<ConsumerRecord<String, String>> messageHandler) {
        if (consumer != null) {
            stopConsuming();
        }

        Properties props = new Properties();
        props.putAll(connectionProps);
        props.put("group.id", groupId);
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());
        props.put("auto.offset.reset", "earliest");
        props.put("enable.auto.commit", "true");

        KafkaConsumer<String, String> nextConsumer = new KafkaConsumer<>(props);
        nextConsumer.subscribe(Collections.singletonList(topic));
        consumer = nextConsumer;
        consuming = true;

        Thread nextConsumerThread = new Thread(() -> {
            try {
                while (consuming) {
                    ConsumerRecords<String, String> records = nextConsumer.poll(Duration.ofMillis(100));
                    for (ConsumerRecord<String, String> record : records) {
                        messageHandler.accept(record);
                    }
                }
            } catch (WakeupException ignored) {
                // wakeup() is the normal way to interrupt a blocking poll during stop.
            } finally {
                nextConsumer.close();
                if (consumer == nextConsumer) {
                    consumer = null;
                }
                if (consumerThread == Thread.currentThread()) {
                    consumerThread = null;
                }
            }
        }, "KafkaConsumerThread");
        consumerThread = nextConsumerThread;
        nextConsumerThread.setDaemon(true);
        nextConsumerThread.start();
    }

    /**
     * 停止消费
     */
    public void stopConsuming() {
        consuming = false;
        KafkaConsumer<String, String> activeConsumer = consumer;
        if (activeConsumer != null) {
            activeConsumer.wakeup();
        }
        Thread activeThread = consumerThread;
        if (activeThread != null && activeThread != Thread.currentThread()) {
            try {
                activeThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 获取最新消息（不加入消费组）
     */
    public List<ConsumerRecord<String, String>> peekMessages(String topic, int partition, int count) {
        Properties props = new Properties();
        props.putAll(connectionProps);
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());
        props.put("enable.auto.commit", "false");

        List<ConsumerRecord<String, String>> messages = new ArrayList<>();
        try (KafkaConsumer<String, String> peekConsumer = new KafkaConsumer<>(props)) {
            TopicPartition tp = new TopicPartition(topic, partition);
            peekConsumer.assign(Collections.singletonList(tp));
            
            // 获取最新 offset
            peekConsumer.seekToEnd(Collections.singletonList(tp));
            long endOffset = peekConsumer.position(tp);
            long startOffset = Math.max(0, endOffset - count);
            
            peekConsumer.seek(tp, startOffset);
            
            while (messages.size() < count) {
                ConsumerRecords<String, String> records = peekConsumer.poll(Duration.ofMillis(1000));
                if (records.isEmpty()) break;
                for (ConsumerRecord<String, String> record : records) {
                    messages.add(record);
                    if (messages.size() >= count) break;
                }
            }
        }
        return messages;
    }

    @Override
    public void close() {
        stopConsuming();
        if (producer != null) {
            producer.close();
            producer = null;
        }
        if (adminClient != null) {
            adminClient.close();
            adminClient = null;
        }
        connectionProps = null;
    }
}
