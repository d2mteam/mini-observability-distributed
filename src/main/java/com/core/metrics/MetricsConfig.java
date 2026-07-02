package com.core.metrics;

/** Cấu hình metrics. slowThresholdMillis: request lâu hơn ngưỡng này tính là "slow". */
public record MetricsConfig(long slowThresholdMillis) {
    public static MetricsConfig defaults() {
        return new MetricsConfig(500);
    }
}
