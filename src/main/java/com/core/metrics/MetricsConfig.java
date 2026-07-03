package com.core.metrics;

public record MetricsConfig(long slowThresholdMillis) {
    public static MetricsConfig defaults() {
        return new MetricsConfig(500);
    }
}
