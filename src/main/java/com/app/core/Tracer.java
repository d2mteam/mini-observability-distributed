package com.app.core;

import com.app.core.Sampler.Sampler;
import com.app.core.propagation.TraceContext;
import com.app.core.propagation.TraceContextHolder;
import com.app.internal.IdGenerator;


public class Tracer {
    private final String serviceName;
    private final SpanDispatcher spanDispatcher;
    private final Sampler sampler;

    public Tracer(String serviceName, SpanDispatcher spanDispatcher, Sampler sampler) {
        this.serviceName = serviceName;
        this.spanDispatcher = spanDispatcher;
        this.sampler = sampler;
    }


    private Span open(String name, Span.Kind kind, TraceContext context) {
        return Span.builder()
                .traceId(context.traceId())
                .spanId(context.spanId())
                .parentSpanId(context.parentSpanId())
                .name(name)
                .kind(kind)
                .serviceName(serviceName)
                .sampled(context.sampled())
                .startEpochMillis(System.currentTimeMillis())
                .build();
    }

    public void finishSpan(Span span) {
        if (span == null) return;
        try {
            span.end(System.currentTimeMillis());
            if (span.isSampled()) {              // không sampled → không xuất, chỉ propagate context
                spanDispatcher.record(span);
            }
        } catch (Exception e) {
            // nuốt: lỗi đo không được hại app
        }
    }

    public TraceContext currentContext() {
        return TraceContextHolder.get();
    }

    public SpanInScope startSpan(String name, Span.Kind kind) {
        TraceContext current = TraceContextHolder.get();
        TraceContext context;
        if (current == null) {
            String newTraceId = IdGenerator.newTraceId();
            boolean isSampled = sampler.isSampled(newTraceId);   // chỉ quyết định 1 lần tại root
            context = TraceContext.root(newTraceId, isSampled);
        } else {
            context = current.child();                           // child kế thừa sampled của trace
        }
        TraceContextHolder.Scope scope = TraceContextHolder.newScope(context);
        Span span = open(name, kind, context);
        return new SpanInScope(this, scope, span);
    }

    public record SpanInScope(Tracer tracer, TraceContextHolder.Scope scope, Span span) implements AutoCloseable {
        @Override
        public void close() {
            tracer.finishSpan(span);                 // end + record + đóng scope
            if (scope != null) {
                scope.close();
            }
        }
    }
}
