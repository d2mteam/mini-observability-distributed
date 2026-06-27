package com.app.core;

import com.app.core.propagation.TraceContext;
import com.app.core.propagation.TraceContextHolder;

public class Tracer {
    private final String serviceName;
    private final SpanDispatcher spanDispatcher;
//    private final Sampler sampler;

    public Tracer(String serviceName, SpanDispatcher spanDispatcher) {
        this.serviceName = serviceName;
        this.spanDispatcher = spanDispatcher;
    }


    public Span startSpan(String name, Span.Kind kind) {
        TraceContext current = TraceContextHolder.get();
        TraceContext context = (current == null) ? TraceContext.root(true) : current.child();
        return open(name, kind, context);
    }

    private Span open(String name, Span.Kind kind, TraceContext context) {
        TraceContextHolder.Scope scope = TraceContextHolder.newScope(context);
        Span span = Span.builder()
                .traceId(context.traceId())
                .spanId(context.spanId())
                .parentSpanId(context.parentSpanId())
                .name(name)
                .kind(kind)
                .serviceName(serviceName)
                .startEpochMillis(System.currentTimeMillis())
                .build();
        span.attachScope(scope);
        return span;
    }

    public void finishSpan(Span span) {
        if (span == null) return;
        try {
            span.end(System.currentTimeMillis());
            spanDispatcher.record(span);
        } catch (Exception e) {
            // nuốt: lỗi đo không được hại app
        } finally {
            span.closeScope();                   // LUÔN khôi phục context cha
        }
    }

    public TraceContext currentContext() {
        return TraceContextHolder.get();
    }

    public SpanInScope startSpan2(String name, Span.Kind kind) {
        TraceContext current = TraceContextHolder.get();
        TraceContext context = (current == null) ? TraceContext.root(true) : current.child();
        return new SpanInScope(this, open(name, kind, context));   // gói span + tracer
    }

    public record SpanInScope(Tracer tracer, Span span) implements AutoCloseable {
        @Override
        public void close() {
            tracer.finishSpan(span);                 // end + record + đóng scope
        }
    }
}
