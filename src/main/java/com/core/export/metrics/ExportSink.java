package com.core.export.metrics;

public interface ExportSink {
    void send(String json);
}