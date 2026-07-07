package com.core.demo;

import com.core.tracing.propagation.TraceContext;
import com.core.tracing.propagation.TraceContextHolder;
import io.micrometer.context.ThreadLocalAccessor;

public final class TraceContextThreadLocalAccessor implements ThreadLocalAccessor<TraceContext> {
    public static final String KEY = "mini.observability.trace.context";

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public TraceContext getValue() {
        return TraceContextHolder.get();
    }

    @Override
    public void setValue(TraceContext value) {
        TraceContextHolder.set(value);
    }

    @Override
    public void setValue() {
        TraceContextHolder.clear();
    }
}
