package com.local.receiver.store;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

final class BoundedRingBuffer<T> {
    private final int capacity;
    private final ArrayDeque<T> values = new ArrayDeque<>();
    private long accepted;
    private long dropped;

    BoundedRingBuffer(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    // Demo backend: retention is just newest-N in memory. No disk, compaction, or paging.
    void addFirst(T value) {
        accepted++;
        values.addFirst(value);
        while (values.size() > capacity) {
            values.removeLast();
            dropped++;
        }
    }

    List<T> snapshotNewestFirst() {
        return List.copyOf(new ArrayList<>(values));
    }

    void clear() {
        values.clear();
        accepted = 0;
        dropped = 0;
    }

    long accepted() {
        return accepted;
    }

    long dropped() {
        return dropped;
    }

    int size() {
        return values.size();
    }

    int capacity() {
        return capacity;
    }
}
