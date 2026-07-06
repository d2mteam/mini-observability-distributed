package com.core.export.tracing;

public interface SpanSink {
    void send(SpanExport spanExport) throws Exception;
}
