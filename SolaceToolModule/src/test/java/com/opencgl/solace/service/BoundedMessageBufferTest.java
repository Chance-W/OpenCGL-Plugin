package com.opencgl.solace.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoundedMessageBufferTest {
    @Test
    void evictsOldestAndDrainsInFifoOrder() {
        var buffer = new BoundedMessageBuffer<String>(3);
        buffer.offer("one");
        buffer.offer("two");
        buffer.offer("three");
        buffer.offer("four");

        assertEquals(1, buffer.droppedCount());
        assertEquals(List.of("two", "three"), buffer.drain(2));
        assertEquals(List.of("four"), buffer.drain(10));
        assertEquals(0, buffer.size());
    }

    @Test
    void clearRemovesPendingItemsWithoutResettingDropStatistics() {
        var buffer = new BoundedMessageBuffer<Integer>(1);
        buffer.offer(1);
        buffer.offer(2);
        buffer.clear();
        assertEquals(0, buffer.size());
        assertEquals(1, buffer.droppedCount());
    }
}
