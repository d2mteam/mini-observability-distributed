package com.core.metrics;

import java.util.Map;

public record MetricsSnapshot(
        long inFlightRequests,
        Map<String, Endpoint> endpoints,
        Map<String, Long> consecutiveFailures) {

    public record Endpoint(long count, long errors, long slow, long totalBytes,
                           long activeConnections,
                           long p50Millis, long p95Millis, long p99Millis) {
        public double errorRatePercent() {
            return count == 0 ? 0.0 : 100.0 * errors / count;
        }
    }

    public static MetricsSnapshot empty() {
        return new MetricsSnapshot(0, Map.of(), Map.of());
    }
}
