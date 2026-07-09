package com.local.receiver.model;

public record EndpointMetricsRecord(long count,
                                    long errors,
                                    long slow,
                                    long totalBytes,
                                    long activeConnections,
                                    long p50Millis,
                                    long p95Millis,
                                    long p99Millis) {
    public double errorRatePercent() {
        return count == 0 ? 0.0 : 100.0 * errors / count;
    }
}
