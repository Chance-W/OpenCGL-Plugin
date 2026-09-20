package com.opencgl.solace.service;

import com.opencgl.solace.model.SolaceConnectionConfig;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Runs a disposable broker connection probe away from the JavaFX thread. */
public final class SolaceConnectionTester {
    private final SolaceRuntimeFactory runtimeFactory;

    public SolaceConnectionTester(SolaceRuntimeFactory runtimeFactory) {
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory, "runtimeFactory");
    }

    public CompletableFuture<Void> test(SolaceConnectionConfig config) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Thread.startVirtualThread(() -> {
            try (SolaceRuntimeConnection ignored = runtimeFactory.open(config)) {
                result.complete(null);
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        });
        return result;
    }
}
