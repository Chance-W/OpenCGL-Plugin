package com.opencgl.solace.service;

import com.opencgl.solace.model.SolaceOutboundMessage;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.Consumer;
import com.solacesystems.jcsmp.ConsumerFlowProperties;
import com.solacesystems.jcsmp.FlowReceiver;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.XMLMessageConsumer;
import com.solacesystems.jcsmp.XMLMessageListener;
import com.solacesystems.jcsmp.XMLMessageProducer;

import com.solacesystems.jcsmp.DeliveryMode;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class JcsmpRuntimeConnection implements SolaceRuntimeConnection {
    private final JCSMPSession session;
    private final SolaceMessageMapper mapper;
    private XMLMessageProducer producer;
    private final Map<Object, CompletableFuture<SolacePublishReceipt>> pending = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    JcsmpRuntimeConnection(JCSMPSession session, SolaceMessageMapper mapper) {
        this.session = session;
        this.mapper = mapper;
    }

    private XMLMessageProducer producer() throws JCSMPException {
        if (producer != null) return producer;
        producer = session.getMessageProducer(new JCSMPStreamingPublishCorrelatingEventHandler() {
            @Override public void responseReceivedEx(Object correlationKey) {
                CompletableFuture<SolacePublishReceipt> future = pending.remove(correlationKey);
                if (future != null) future.complete(new SolacePublishReceipt(
                    SolacePublishReceipt.Status.BROKER_ACK, String.valueOf(correlationKey),
                    System.currentTimeMillis(), "Broker ACK"));
            }

            @Override public void handleErrorEx(Object correlationKey, JCSMPException cause, long timestamp) {
                CompletableFuture<SolacePublishReceipt> future = pending.remove(correlationKey);
                if (future != null) future.completeExceptionally(cause);
            }
            @Override public void responseReceived(String messageId) { completeLegacyAck(messageId, null); }
            @Override public void handleError(String messageId, JCSMPException error, long timestamp) {
                completeLegacyAck(messageId, error);
            }
        });
        return producer;
    }

    private void completeLegacyAck(String messageId, JCSMPException error) {
        if (pending.size() != 1) return;
        Object key = pending.keySet().iterator().next();
        CompletableFuture<SolacePublishReceipt> future = pending.remove(key);
        if (future == null) return;
        if (error != null) future.completeExceptionally(error);
        else future.complete(new SolacePublishReceipt(SolacePublishReceipt.Status.BROKER_ACK,
            String.valueOf(key), System.currentTimeMillis(), "Broker ACK"));
    }

    @Override public CompletableFuture<SolacePublishReceipt> send(SolaceOutboundMessage source) throws Exception {
        var mapped = mapper.toJcsmp(source);
        String correlationKey = UUID.randomUUID().toString();
        mapped.message().setCorrelationKey(correlationKey);
        if (source.deliveryMode() == DeliveryMode.DIRECT) {
            producer().send(mapped.message(), mapped.destination());
            return CompletableFuture.completedFuture(new SolacePublishReceipt(
                SolacePublishReceipt.Status.DIRECT_ACCEPTED, correlationKey, System.currentTimeMillis(),
                "Direct 消息已写入发送缓冲区（Direct 模式无 Broker ACK）"));
        }

        CompletableFuture<SolacePublishReceipt> result = new CompletableFuture<>();
        pending.put(correlationKey, result);
        try {
            producer().send(mapped.message(), mapped.destination());
        } catch (Exception error) {
            pending.remove(correlationKey);
            result.completeExceptionally(error);
        }
        return result.orTimeout(10, TimeUnit.SECONDS)
            .whenComplete((receipt, error) -> pending.remove(correlationKey));
    }

    @Override public ListenerHandle listen(SolaceListenerConfig config, MessageHandler handler) throws Exception {
        if (config.destinationName().isBlank()) throw new IllegalArgumentException("destination is required");
        XMLMessageListener listener = new XMLMessageListener() {
            @Override public void onReceive(BytesXMLMessage message) {
                String destination = message.getDestination() == null ? config.destinationName()
                    : message.getDestination().getName();
                handler.onMessage(mapper.fromJcsmp(message, destination, System.currentTimeMillis()));
                if (config.clientAcknowledge()) message.ackMessage();
            }
            @Override public void onException(JCSMPException error) { handler.onError(error); }
        };

        Consumer consumer;
        if (config.destinationType() == SolaceListenerConfig.DestinationType.QUEUE) {
            ConsumerFlowProperties flow = new ConsumerFlowProperties();
            flow.setEndpoint(JCSMPFactory.onlyInstance().createQueue(config.destinationName()));
            flow.setAckMode(config.clientAcknowledge()
                ? JCSMPProperties.SUPPORTED_MESSAGE_ACK_CLIENT
                : JCSMPProperties.SUPPORTED_MESSAGE_ACK_AUTO);
            flow.setStartState(true);
            FlowReceiver receiver = session.createFlow(listener, flow);
            consumer = receiver;
        } else {
            XMLMessageConsumer directConsumer = session.getMessageConsumer(listener);
            session.addSubscription(JCSMPFactory.onlyInstance().createTopic(config.destinationName()));
            directConsumer.start();
            consumer = directConsumer;
        }
        return new ConsumerHandle(consumer);
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        IllegalStateException error = new IllegalStateException("Solace connection closed before publish acknowledgement");
        pending.values().forEach(future -> future.completeExceptionally(error));
        pending.clear();
        try { if (producer != null) producer.close(); } catch (RuntimeException ignored) { }
        session.closeSession();
    }

    private static final class ConsumerHandle implements ListenerHandle {
        private final Consumer consumer;
        private final AtomicBoolean closed = new AtomicBoolean();
        private ConsumerHandle(Consumer consumer) { this.consumer = consumer; }
        @Override public void pause() { if (!closed.get()) consumer.stop(); }
        @Override public void resume() throws Exception { if (!closed.get()) consumer.start(); }
        @Override public void close() {
            if (closed.compareAndSet(false, true)) consumer.close();
        }
    }
}
