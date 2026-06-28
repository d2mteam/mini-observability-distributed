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

    /** Trace mới: tự sinh traceId + spanId, parent = null, tự quyết sampling (1 lần tại gốc). */
    private TraceContext rootContext() {
        String traceId = IdGenerator.newTraceId();
        return new TraceContext(traceId, IdGenerator.newSpanId(), null, sampler.isSampled(traceId));
    }

    /** Span kế tiếp sau {@code parent}: spanId mới, parent = parent.spanId, kế thừa trace + sampled. */
    private TraceContext nextContext(TraceContext parent) {
        return new TraceContext(parent.traceId(), IdGenerator.newSpanId(), parent.spanId(), parent.sampled());
    }

    private SpanInScope begin(String name, Span.Kind kind, TraceContext context) {
        TraceContextHolder.Scope scope = TraceContextHolder.newScope(context);
        Span span = open(name, kind, context);
        return new SpanInScope(this, scope, span);
    }

    public SpanInScope startSpan(String name, Span.Kind kind) {
        TraceContext current = TraceContextHolder.get();
        TraceContext context = (current == null)
                ? rootContext()            // không có cha trong thread → mở trace mới
                : nextContext(current);    // có cha trong thread → nối tiếp 1 hop
        return begin(name, kind, context);
    }

    /**
     * Điểm vào INBOUND (server). {@code remoteParent} là context CHA đã extract từ header đến
     * (span của upstream); Tracer tự mint span của CHÍNH MÌNH từ nó qua {@link #nextContext}.
     * remoteParent != null → nối tiếp trace của upstream; null → không có context đến → trace mới.
     * Không tham chiếu ThreadLocal: ở biên server, header mới là nguồn context đáng tin.
     */
    public SpanInScope startServer(String name, TraceContext remoteParent) {
        TraceContext context = (remoteParent != null) ? nextContext(remoteParent) : rootContext();
        return begin(name, Span.Kind.SERVER, context);
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
