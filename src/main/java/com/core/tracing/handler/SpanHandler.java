package com.core.tracing.handler;

import com.core.tracing.Span;

public interface SpanHandler {
    void handle(Span span);
}