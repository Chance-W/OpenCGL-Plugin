package com.opencgl.sshjedi.lifecycle;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Runs a group of independent cleanup actions at most once. */
public final class LifecycleDisposer {

    @FunctionalInterface
    public interface CleanupAction {
        void run() throws Exception;
    }

    private final AtomicBoolean disposed = new AtomicBoolean();

    public void dispose(Consumer<Throwable> onFailure, CleanupAction... actions) {
        Objects.requireNonNull(onFailure, "onFailure");
        if (!disposed.compareAndSet(false, true)) {
            return;
        }
        for (CleanupAction action : actions) {
            if (action == null) {
                continue;
            }
            try {
                action.run();
            } catch (Exception error) {
                onFailure.accept(error);
            }
        }
    }

    public boolean isDisposed() {
        return disposed.get();
    }
}
