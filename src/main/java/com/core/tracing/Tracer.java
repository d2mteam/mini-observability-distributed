package com.core.tracing;

import com.core.tracing.Sampler.Sampler;
import com.core.tracing.propagation.TraceContext;
import com.core.tracing.propagation.TraceContextHolder;


public class Tracer {
    private final SpanDispatcher spanDispatcher;
    private final Sampler sampler;

    public Tracer(SpanDispatcher spanDispatcher, Sampler sampler) {
        this.spanDispatcher = spanDispatcher;
        this.sampler = sampler;
    }


    private Span open(TraceContext context) {
        return Span.builder()
                .traceId(context.traceId())
                .spanId(context.spanId())
                .parentSpanId(context.parentSpanId())
                .sampled(context.sampled())
                .startEpochMillis(System.currentTimeMillis())
                .startNanos(System.nanoTime())
                .build();
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

    // ===== Vòng đời span (giống Brave): nextSpan → withSpanInScope → finishSpan =====

    /** Span con của span hiện tại trên thread (chưa có → root mới). Đặt name/kind qua mutator của Span. */
    public Span nextSpan() {
        TraceContext current = TraceContextHolder.get();
        return open(current == null ? rootContext() : nextContext(current));
    }

    /**
     * Span nối tiếp context lấy từ INBOUND (đã extract): {@code remoteParent} là span upstream;
     * null → trace mới. Không đọc ThreadLocal — ở biên server header mới là nguồn đáng tin.
     */
    public Span nextSpan(TraceContext remoteParent) {
        return open(remoteParent == null ? rootContext() : nextContext(remoteParent));
    }

    /**
     * Đưa span ĐÃ CÓ vào thread context. {@code close()} CHỈ pop scope — KHÔNG finish span
     * (finish tách riêng: {@link #finishSpan(Span)}). Nhờ vậy scope ở thread này, finish ở thread khác.
     */
    public SpanInScope withSpanInScope(Span span) {
        TraceContext ctx = new TraceContext(span.getTraceId(), span.getSpanId(),
                span.getParentSpanId(), span.isSampled());
        return new SpanInScope(TraceContextHolder.newScope(ctx));
    }

    /**
     * Kết thúc span: chốt duration/status rồi record (nếu sampled). Tracer lo việc này vì nó giữ
     * dispatcher — Span chỉ là dữ liệu. Idempotent; nuốt lỗi để việc đo không hại app.
     */
    public void finishSpan(Span span) {
        if (span == null || span.isFinished()) return;
        span.end(System.nanoTime());
        if (span.isSampled()) {                          // không sampled → không xuất, chỉ propagate
            try {
                spanDispatcher.dispatch(span);
            } catch (Exception e) {
                // nuốt: lỗi đo không được hại app
            }
        }
    }

    /** Giống Brave: close() CHỈ pop scope. Finish/record do {@link #finishSpan(Span)} lo. */
    public record SpanInScope(TraceContextHolder.Scope scope) implements AutoCloseable {
        @Override
        public void close() {
            scope.close();
        }
    }
}
