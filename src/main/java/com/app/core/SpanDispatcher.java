package com.app.core;

import com.app.core.handler.SpanHandler;

import java.util.List;

public class SpanDispatcher {
    private final List<SpanHandler> handlers;

    public SpanDispatcher(List<SpanHandler> handlers) {
        this.handlers = handlers;
    }

    void record(Span span) {
        for (SpanHandler h : handlers) {
            try {
                h.handle(span);
            } catch (Exception e) {
                /* fail-safe TỪNG handler */
            }
        }
    }
}
