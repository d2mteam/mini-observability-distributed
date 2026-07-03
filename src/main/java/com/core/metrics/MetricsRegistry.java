package com.core.metrics;

public interface MetricsRegistry {
    default void onRequestStart() {}

    default void onRequestEnd(String endpoint, long durationMillis, boolean error, long bytes) {}

    default void onConnectionOpened(String endpoint) {}

    default void onConnectionClosed(String endpoint) {}

    default void onDestinationResult(String destination, boolean ok) {}

    default MetricsSnapshot snapshot() {
        return MetricsSnapshot.empty();
    }
}
