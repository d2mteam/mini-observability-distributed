package com.core.tracing;

import com.core.tracing.handler.SpanHandler;

import java.util.List;

public class SpanDispatcher {
    private final List<SpanHandler> handlers;

    public SpanDispatcher(List<SpanHandler> handlers) {
        this.handlers = handlers;
    }

    void dispatch(Span span) {
        for (SpanHandler h : handlers) {
            try {
                h.handle(span);
            } catch (Exception e) {
                /* fail-safe TỪNG handler */
            }
        }
    }
}
