package com.opencgl.solace.service;

import com.opencgl.solace.model.SolaceConnectionConfig;
import com.opencgl.solace.model.SolaceOutboundMessage;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Set;

/** Serial asynchronous publisher using the isolated session lifecycle proven by OpenCGL-WEB. */
public final class SolacePublisherService implements AutoCloseable {
    private final SolaceRuntimeFactory runtimeFactory;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "opencgl-solace-publisher");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<SolaceRuntimeConnection> activeConnections = ConcurrentHashMap.newKeySet();
    private final Set<CompletableFuture<SolacePublishReceipt>> inFlight = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public SolacePublisherService(SolaceRuntimeFactory runtimeFactory) {
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory, "runtimeFactory");
    }

    public CompletableFuture<SolacePublishReceipt> send(SolaceConnectionConfig config, SolaceOutboundMessage message) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("publisher is closed"));
        return CompletableFuture.supplyAsync(() -> {
            try {
                SolaceRuntimeConnection connection = runtimeFactory.open(config);
                activeConnections.add(connection);
                return connection;
            } catch (Exception error) {
                throw new CompletionException(error);
            }
        }, executor).thenCompose(connection -> {
            try {
                CompletableFuture<SolacePublishReceipt> result = connection.send(message);
                inFlight.add(result);
                return result.whenComplete((receipt, error) -> {
                    inFlight.remove(result);
                    activeConnections.remove(connection);
                    connection.close();
                });
            } catch (Exception error) {
                activeConnections.remove(connection);
                connection.close();
                return CompletableFuture.failedFuture(error);
            }
        });
    }

    @Override
    public void close() {
        closed = true;
        IllegalStateException error = new IllegalStateException("publisher closed before send completed");
        inFlight.forEach(future -> future.completeExceptionally(error));
        inFlight.clear();
        activeConnections.forEach(SolaceRuntimeConnection::close);
        activeConnections.clear();
        executor.shutdownNow();
    }
}
