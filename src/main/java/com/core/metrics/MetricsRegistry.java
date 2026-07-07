package com.core.metrics;

public interface MetricsRegistry {
    default void onServerRequestStart() {}

    default void onServerRequestEnd(String route, long durationMillis, boolean error, long bytes) {}

    default void onClientCallStart() {}

    default void onClientCallEnd(String destination, long durationMillis, boolean error, long bytes) {}

    /**
     * Backward-compatible alias for older demos. New interceptors should call
     * server/client-specific methods so the snapshot keeps those two views apart.
     */
    @Deprecated
    default void onRequestStart() {
        onServerRequestStart();
    }

    @Deprecated
    default void onRequestEnd(String endpoint, long durationMillis, boolean error, long bytes) {
        onServerRequestEnd(endpoint, durationMillis, error, bytes);
    }

    default void onConnectionOpened(String endpoint) {}

    default void onConnectionClosed(String endpoint) {}

    default void onDestinationResult(String destination, boolean ok) {}

    default MetricsSnapshot snapshot() {
        return MetricsSnapshot.empty();
    }
}
