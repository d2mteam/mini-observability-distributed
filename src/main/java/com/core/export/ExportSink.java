package com.core.export;

public interface ExportSink {
    void send(String json);
}