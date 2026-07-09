package com.local.receiver.store;

public record StoreStats(long accepted,
                         long dropped,
                         int stored,
                         int capacity) {
}
