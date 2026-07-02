package com.core.core;

import com.core.tracing.Sampler.Sampler;
import com.core.tracing.Span;
import com.core.tracing.SpanDispatcher;
import com.core.tracing.Tracer;
import com.core.tracing.handler.SpanHandler;
import com.core.tracing.propagation.TraceContext;
import com.core.tracing.propagation.TraceContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TracerTest {

    private final List<Span> recorded = new CopyOnWriteArrayList<>();

    private Tracer tracer(Sampler sampler) {
        recorded.clear();
        SpanHandler collector = recorded::add;
        return new Tracer("test", new SpanDispatcher(List.of(collector)), sampler);
    }

    @AfterEach
    void noContextLeak() {
        assertNull(TraceContextHolder.get(), "context bị rò sau test");
    }

    @Test
    void rootHasNoParentAndRecordsOnFinish() {
        Tracer t = tracer(Sampler.ALWAYS_SAMPLE);
        Span root = t.nextSpan().name("root").kind(Span.Kind.SERVER);
        try (var ws = t.withSpanInScope(root)) {
            assertNotNull(TraceContextHolder.get());
        }
        t.finishSpan(root);

        assertEquals(1, recorded.size());
        assertNull(recorded.get(0).getParentSpanId());
        assertTrue(recorded.get(0).isFinished());
    }

    @Test
    void childInheritsTraceAndParentsToCurrent() {
        Tracer t = tracer(Sampler.ALWAYS_SAMPLE);
        Span root = t.nextSpan().name("root").kind(Span.Kind.SERVER);
        try (var ws = t.withSpanInScope(root)) {
            Span child = t.nextSpan().name("child").kind(Span.Kind.CLIENT);
            assertEquals(root.getTraceId(), child.getTraceId());
            assertEquals(root.getSpanId(), child.getParentSpanId());
            t.finishSpan(child);
        }
        t.finishSpan(root);

        assertEquals(2, recorded.size());
    }

    @Test
    void neverSampleDoesNotRecordButStillPropagates() {
        Tracer t = tracer(Sampler.NEVER_SAMPLE);
        Span s = t.nextSpan().name("x").kind(Span.Kind.CLIENT);
        assertFalse(s.isSampled());
        try (var ws = t.withSpanInScope(s)) {
            assertNotNull(TraceContextHolder.get());   // vẫn propagate context
        }
        t.finishSpan(s);
        assertTrue(recorded.isEmpty());                // nhưng KHÔNG record
    }

    @Test
    void finishIsIdempotent() {
        Tracer t = tracer(Sampler.ALWAYS_SAMPLE);
        Span s = t.nextSpan().name("x").kind(Span.Kind.CLIENT);
        t.finishSpan(s);
        t.finishSpan(s);
        assertEquals(1, recorded.size());              // gọi 2 lần không record trùng
    }

    @Test
    void scopeOnlyPops_finishIsSeparate() {
        Tracer t = tracer(Sampler.ALWAYS_SAMPLE);
        Span s = t.nextSpan().name("x").kind(Span.Kind.CLIENT);
        try (var ws = t.withSpanInScope(s)) {
            assertNotNull(TraceContextHolder.get());
        }
        assertNull(TraceContextHolder.get());          // close() đã pop scope
        assertFalse(s.isFinished());                   // nhưng span CHƯA finish
        assertTrue(recorded.isEmpty());

        t.finishSpan(s);
        assertTrue(s.isFinished());
        assertEquals(1, recorded.size());
    }

    @Test
    void asyncFinishOnDifferentThread() throws Exception {
        Tracer t = tracer(Sampler.ALWAYS_SAMPLE);
        Span span = t.nextSpan().name("async").kind(Span.Kind.CLIENT);   // tạo ở thread test

        Thread worker = new Thread(() -> {
            try (var ws = t.withSpanInScope(span)) {
                assertEquals(span.getSpanId(), TraceContextHolder.get().spanId());
            }
            t.finishSpan(span);                                           // finish ở thread khác
        });
        worker.start();
        worker.join();

        assertNull(TraceContextHolder.get());          // thread test không bị rò
        assertEquals(1, recorded.size());
        assertEquals("async", recorded.get(0).getName());
    }

    @Test
    void serverSpanContinuesRemoteTrace() {
        Tracer t = tracer(Sampler.ALWAYS_SAMPLE);
        TraceContext remoteParent =
                new TraceContext("0af7651916cd43dd8448eb211c80319c", "b7ad6b7169203331", null, true);

        Span server = t.nextSpan(remoteParent).name("GET /x").kind(Span.Kind.SERVER);

        assertEquals(remoteParent.traceId(), server.getTraceId());        // cùng trace upstream
        assertEquals(remoteParent.spanId(), server.getParentSpanId());    // parent = span upstream
        t.finishSpan(server);
    }
}
