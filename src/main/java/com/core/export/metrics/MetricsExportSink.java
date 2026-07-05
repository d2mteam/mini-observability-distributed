package com.core.export.metrics;

public interface MetricsExportSink {
    void send(String json);
}
