package com.core.tracing.handler;

import com.core.tracing.Span;
import lombok.Getter;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class SimpleStoreSpanHandler implements SpanHandler {
    @Getter
    private final BlockingQueue<Span> queue =  new LinkedBlockingQueue<>();

    @Override
    public void handle(Span span) {
        queue.add(span);
    }
}
