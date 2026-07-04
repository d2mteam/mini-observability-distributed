package com.core.export.tracing;

public interface SpanSink {
    void send(String json) throws Exception;
}
