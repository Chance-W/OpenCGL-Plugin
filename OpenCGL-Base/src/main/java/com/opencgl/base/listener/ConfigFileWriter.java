package com.opencgl.base.listener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Serializes configuration writes and replaces target files atomically. */
final class ConfigFileWriter implements AutoCloseable {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "opencgl-config-writer");
        thread.setDaemon(true);
        return thread;
    });
    private CompletableFuture<Void> pending = CompletableFuture.completedFuture(null);

    synchronized CompletableFuture<Void> submit(Path target, String content) {
        pending = pending.handle((ignored, previousFailure) -> null)
            .thenRunAsync(() -> {
                try {
                    writeAtomically(target, content);
                } catch (IOException error) {
                    throw new CompletionException(error);
                }
            }, executor);
        return pending;
    }

    boolean awaitPendingWrites(Duration timeout) {
        CompletableFuture<Void> snapshot;
        synchronized (this) {
            snapshot = pending;
        }
        try {
            snapshot.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    static void writeAtomically(Path target, String content) throws IOException {
        Path absoluteTarget = target.toAbsolutePath();
        Path parent = absoluteTarget.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = absoluteTarget.resolveSibling(absoluteTarget.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, absoluteTarget,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(temporary, absoluteTarget, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void close() {
        awaitPendingWrites(Duration.ofSeconds(5));
        executor.shutdown();
    }
}
