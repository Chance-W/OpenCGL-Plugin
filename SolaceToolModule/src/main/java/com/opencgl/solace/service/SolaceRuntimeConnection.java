package com.opencgl.solace.service;

import com.opencgl.solace.model.SolaceOutboundMessage;
import com.opencgl.solace.model.SolaceReceivedMessage;

import java.util.concurrent.CompletableFuture;

public interface SolaceRuntimeConnection extends AutoCloseable {
    CompletableFuture<SolacePublishReceipt> send(SolaceOutboundMessage message) throws Exception;
    ListenerHandle listen(SolaceListenerConfig config, MessageHandler handler) throws Exception;

    @Override void close();

    interface MessageHandler {
        void onMessage(SolaceReceivedMessage message);
        default void onError(Throwable error) { }
    }

    interface ListenerHandle extends AutoCloseable {
        void pause();
        void resume() throws Exception;
        @Override void close();
    }
}
