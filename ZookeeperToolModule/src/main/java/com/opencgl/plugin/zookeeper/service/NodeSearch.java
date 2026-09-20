package com.opencgl.plugin.zookeeper.service;

import org.I0Itec.zkclient.exception.ZkNoNodeException;
import org.apache.zookeeper.KeeperException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Read-only traversal. No JavaFX objects or node data are accessed by the worker. */
public final class NodeSearch {
    @FunctionalInterface public interface ChildrenReader {
        List<String> children(String path) throws Exception;
    }
    public record Progress(int visited, int matched, int skipped) {}
    public record Result(List<String> matches, int visited, int skipped, boolean truncated) {}

    private NodeSearch() {}

    public static boolean matches(String path, String query) {
        return path.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));
    }

    public static Result scan(ChildrenReader reader, String query, BooleanSupplier cancelled,
                              Consumer<Progress> progress) throws Exception {
        return scan(reader, query, cancelled, progress, 20_000);
    }

    static Result scan(ChildrenReader reader, String query, BooleanSupplier cancelled,
                       Consumer<Progress> progress, int limit) throws Exception {
        var pending = new ArrayDeque<String>(); pending.add("/");
        var matches = new ArrayList<String>();
        int visited = 0, skipped = 0;
        boolean truncated = false;
        while (!pending.isEmpty() && visited < limit && matches.size() < 2_000) {
            checkCancelled(cancelled);
            String path = pending.removeFirst();
            visited++;
            List<String> children;
            try {
                children = reader.children(path);
            } catch (Exception error) {
                checkCancelled(cancelled);
                if (!skippable(error)) throw error;
                skipped++;
                progress.accept(new Progress(visited, matches.size(), skipped));
                continue;
            }
            checkCancelled(cancelled);
            if (matches(path, query)) matches.add(path);
            for (String child : children) {
                checkCancelled(cancelled);
                if (visited + pending.size() >= limit) { truncated = true; break; }
                pending.addLast(path.equals("/") ? "/" + child : path + "/" + child);
            }
            progress.accept(new Progress(visited, matches.size(), skipped));
        }
        return new Result(List.copyOf(matches), visited, skipped, truncated || !pending.isEmpty());
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException();
    }

    private static boolean skippable(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ZkNoNodeException) return true;
            if (cause instanceof KeeperException zk &&
                    (zk.code() == KeeperException.Code.NONODE || zk.code() == KeeperException.Code.NOAUTH)) return true;
        }
        return false;
    }
}
