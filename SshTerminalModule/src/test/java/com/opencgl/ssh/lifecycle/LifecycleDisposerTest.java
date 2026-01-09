package com.opencgl.ssh.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LifecycleDisposerTest {

    @Test
    void runsEveryCleanupOnceAndIsolatesFailures() {
        LifecycleDisposer disposer = new LifecycleDisposer();
        AtomicInteger completed = new AtomicInteger();
        List<Throwable> failures = new ArrayList<>();

        disposer.dispose(failures::add,
            () -> { throw new IllegalStateException("first cleanup failed"); },
            completed::incrementAndGet);
        disposer.dispose(failures::add, completed::incrementAndGet);

        assertTrue(disposer.isDisposed());
        assertEquals(1, completed.get());
        assertEquals(1, failures.size());
        assertEquals("first cleanup failed", failures.getFirst().getMessage());
    }
}
