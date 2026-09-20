package com.opencgl.plugin.zookeeper.service;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class NodeSearchTest {
    @Test void matchesNamesAndFullPathsIgnoringCaseWithoutChangingPaths() {
        assertTrue(NodeSearch.matches("/services/OrderProvider", " order "));
        assertTrue(NodeSearch.matches("/services/OrderProvider", "SERVICES/order"));
        assertFalse(NodeSearch.matches("/services/Payment", "order"));
    }

    @Test void searchesUnexpandedDescendantsAndReportsProgress() throws Exception {
        Map<String, List<String>> children = Map.of("/", List.of("apps", "other"),
                "/apps", List.of("Order"), "/apps/Order", List.of("worker"),
                "/apps/Order/worker", List.of(), "/other", List.of());
        var progress = new ArrayList<NodeSearch.Progress>();
        var result = NodeSearch.scan(children::get, "order", () -> false, progress::add);
        assertEquals(List.of("/apps/Order", "/apps/Order/worker"), result.matches());
        assertEquals(5, result.visited());
        assertEquals(0, result.skipped());
        assertFalse(result.truncated());
        assertEquals(5, progress.get(progress.size() - 1).visited());
    }

    @Test void cancellationStopsBeforeReadingAnotherNode() {
        var calls = new AtomicInteger();
        assertThrows(CancellationException.class, () -> NodeSearch.scan(path -> {
            calls.incrementAndGet(); return List.of("child");
        }, "child", () -> calls.get() == 1, p -> {}));
        assertEquals(1, calls.get());
    }

    @Test void missingAndForbiddenBranchesAreSkippedButConnectionErrorsPropagate() throws Exception {
        var result = NodeSearch.scan(path -> {
            if (path.equals("/")) return List.of("gone", "private", "ok");
            if (path.equals("/gone")) throw new org.I0Itec.zkclient.exception.ZkNoNodeException();
            if (path.equals("/private")) throw org.I0Itec.zkclient.exception.ZkException.create(
                    new org.apache.zookeeper.KeeperException.NoAuthException());
            return List.of();
        }, "ok", () -> false, p -> {});
        assertEquals(List.of("/ok"), result.matches());
        assertEquals(2, result.skipped());
        assertThrows(IllegalStateException.class, () -> NodeSearch.scan(path -> {
            throw new IllegalStateException("Disconnected");
        }, "ok", () -> false, p -> {}));
    }

    @Test void traversalHasABoundForVeryLargeTrees() throws Exception {
        var result = NodeSearch.scan(path -> List.of("child"), "nothing", () -> false, p -> {}, 10);
        assertEquals(10, result.visited());
        assertTrue(result.truncated());
    }
}
