package com.opencgl.solace.service;

import com.opencgl.solace.model.SolaceConnectionConfig;
import com.opencgl.solace.model.SolaceReceivedMessage;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Asynchronous listener lifecycle with a bounded display buffer. */
public final class SolaceListenerService implements AutoCloseable {
    private final SolaceRuntimeFactory runtimeFactory;
    private final BoundedMessageBuffer<SolaceReceivedMessage> buffer;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "opencgl-solace-listener");
        thread.setDaemon(true);
        return thread;
    });
    private SolaceRuntimeConnection connection;
    private SolaceRuntimeConnection.ListenerHandle listener;
    private volatile Throwable lastError;

    public SolaceListenerService(SolaceRuntimeFactory runtimeFactory, int capacity) {
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory, "runtimeFactory");
        this.buffer = new BoundedMessageBuffer<>(capacity);
    }

    public CompletableFuture<Void> start(SolaceConnectionConfig connectionConfig, SolaceListenerConfig listenerConfig) {
        return CompletableFuture.runAsync(() -> {
            try {
                stopInternal();
                connection = runtimeFactory.open(connectionConfig);
                listener = connection.listen(listenerConfig, new SolaceRuntimeConnection.MessageHandler() {
                    @Override public void onMessage(SolaceReceivedMessage message) { buffer.offer(message); }
                    @Override public void onError(Throwable error) { lastError = error; }
                });
            } catch (Exception error) {
                stopInternal();
                throw new RuntimeException(error);
            }
        }, executor);
    }

    public void pause() { if (listener != null) listener.pause(); }
    public void resume() throws Exception { if (listener != null) listener.resume(); }
    public List<SolaceReceivedMessage> drain(int maximum) { return buffer.drain(maximum); }
    public long droppedCount() { return buffer.droppedCount(); }
    public Throwable lastError() { return lastError; }
    public void clear() { buffer.clear(); }

    public CompletableFuture<Void> stop() {
        return CompletableFuture.runAsync(this::stopInternal, executor);
    }

    private void stopInternal() {
        SolaceRuntimeConnection.ListenerHandle currentListener = listener;
        listener = null;
        if (currentListener != null) currentListener.close();
        SolaceRuntimeConnection currentConnection = connection;
        connection = null;
        if (currentConnection != null) currentConnection.close();
    }

    @Override public void close() {
        stopInternal();
        executor.shutdownNow();
    }
}
