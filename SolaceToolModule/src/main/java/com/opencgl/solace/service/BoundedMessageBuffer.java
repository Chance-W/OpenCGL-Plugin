package com.opencgl.solace.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Thread-safe bounded FIFO used between JCSMP callbacks and JavaFX batch updates. */
public final class BoundedMessageBuffer<T> {
    private final int capacity;
    private final ArrayDeque<T> queue;
    private long droppedCount;

    public BoundedMessageBuffer(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
        this.queue = new ArrayDeque<>(capacity);
    }

    public synchronized void offer(T value) {
        Objects.requireNonNull(value, "value");
        if (queue.size() == capacity) {
            queue.removeFirst();
            droppedCount++;
        }
        queue.addLast(value);
    }

    public synchronized List<T> drain(int limit) {
        if (limit <= 0 || queue.isEmpty()) return List.of();
        int count = Math.min(limit, queue.size());
        List<T> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(queue.removeFirst());
        return result;
    }

    public synchronized void clear() { queue.clear(); }
    public synchronized int size() { return queue.size(); }
    public synchronized long droppedCount() { return droppedCount; }
}
