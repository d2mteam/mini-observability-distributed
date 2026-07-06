package com.core.export.metrics;

public interface MetricsExportSink {
    void send(MetricsExport metricsExport) throws Exception;
}
