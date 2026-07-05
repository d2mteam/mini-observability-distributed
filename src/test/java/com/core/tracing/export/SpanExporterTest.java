package com.core.tracing.export;

import com.core.export.ServiceIdentity;
import com.core.export.tracing.SpanExporter;
import com.core.export.tracing.SpanSink;
import com.core.tracing.Span;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpanExporterTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ServiceIdentity IDENTITY = new ServiceIdentity("orders", "instance-1");

    @Test
    void handleEnqueuesAndFlushExportsPrettyJsonBatch() throws Exception {
        CapturingSpanSink sink = new CapturingSpanSink();
        SpanExporter exporter = exporter(sink, 10, 10, 50);

        exporter.handle(span("0000000000000001"));
        exporter.flush();

        assertEquals(1, sink.payloads.size());
        assertEquals(0, exporter.droppedCount());
        assertTrue(sink.payloads.get(0).contains("\n"), "expected pretty JSON");

        JsonNode json = MAPPER.readTree(sink.payloads.get(0));
        assertEquals("orders", json.get("serviceName").asText());
        assertEquals("instance-1", json.get("instanceId").asText());
        assertEquals(1, json.get("spans").size());
        assertEquals("0000000000000001", json.get("spans").get(0).get("spanId").asText());
    }

    @Test
    void builderUsesDefaultBatchSettings() {
        CapturingSpanSink sink = new CapturingSpanSink();
        SpanExporter exporter = SpanExporter.builder()
                .serviceIdentity(IDENTITY)
                .spanSink(sink)
                .build();

        exporter.handle(span("0000000000000001"));
        exporter.flush();

        assertEquals(1, sink.payloads.size());
    }

    @Test
    void dropsWhenQueueIsFull() {
        CapturingSpanSink sink = new CapturingSpanSink();
        SpanExporter exporter = exporter(sink, 1, 10, 50);

        exporter.handle(span("0000000000000001"));
        exporter.handle(span("0000000000000002"));
        exporter.flush();

        assertEquals(1, exporter.droppedCount());
        assertEquals(1, sink.payloads.size());
    }

    @Test
    void startRunsWorkerAndExportsBatch() throws Exception {
        LatchingSpanSink sink = new LatchingSpanSink(1);
        SpanExporter exporter = exporter(sink, 10, 2, 20);

        exporter.start();
        exporter.start();
        exporter.handle(span("0000000000000001"));

        assertTrue(sink.await(), "worker did not export batch");
        assertEquals(1, sink.payloads.size());
        exporter.close();
    }

    @Test
    void closeFlushesRemainingSpans() {
        CapturingSpanSink sink = new CapturingSpanSink();
        SpanExporter exporter = exporter(sink, 10, 10, 50);

        exporter.handle(span("0000000000000001"));
        exporter.close();

        assertEquals(1, sink.payloads.size());
    }

    @Test
    void closeClosesAutoCloseableSink() {
        ClosingSpanSink sink = new ClosingSpanSink();
        SpanExporter exporter = exporter(sink, 10, 10, 50);

        exporter.close();

        assertEquals(true, sink.closed);
    }

    @Test
    void sinkFailureDoesNotEscape() {
        ThrowingSpanSink sink = new ThrowingSpanSink();
        SpanExporter exporter = exporter(sink, 10, 10, 50);

        exporter.handle(span("0000000000000001"));

        assertDoesNotThrow(exporter::flush);
        assertEquals(1, sink.calls);
    }

    @Test
    void flushDoesNothingWhenQueueIsEmpty() {
        CapturingSpanSink sink = new CapturingSpanSink();
        SpanExporter exporter = exporter(sink, 10, 10, 50);

        exporter.flush();

        assertEquals(0, sink.payloads.size());
    }

    private static Span span(String spanId) {
        Span span = Span.builder()
                .traceId("0af7651916cd43dd8448eb211c80319c")
                .spanId(spanId)
                .parentSpanId("1111111111111111")
                .sampled(true)
                .startEpochMillis(1_000)
                .build()
                .name("GET /orders/{id}")
                .kind(Span.Kind.SERVER)
                .tag("component", "test");
        span.status(Span.Status.OK);
        return span;
    }

    private static SpanExporter exporter(SpanSink sink,
                                         int queueCapacity,
                                         int batchSize,
                                         long maxDelayMillis) {
        return SpanExporter.builder()
                .serviceIdentity(IDENTITY)
                .spanSink(sink)
                .queueCapacity(queueCapacity)
                .batchSize(batchSize)
                .maxDelayMillis(maxDelayMillis)
                .build();
    }

    private static class CapturingSpanSink implements SpanSink {
        final List<String> payloads = new ArrayList<>();

        @Override
        public void send(String json) {
            payloads.add(json);
        }
    }

    private static class LatchingSpanSink extends CapturingSpanSink {
        private final CountDownLatch latch;

        LatchingSpanSink(int expectedSends) {
            this.latch = new CountDownLatch(expectedSends);
        }

        @Override
        public void send(String json) {
            super.send(json);
            latch.countDown();
        }

        boolean await() throws InterruptedException {
            return latch.await(2, TimeUnit.SECONDS);
        }
    }

    private static class ClosingSpanSink extends CapturingSpanSink implements AutoCloseable {
        boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }

    private static class ThrowingSpanSink implements SpanSink {
        int calls;

        @Override
        public void send(String json) {
            calls++;
            throw new IllegalStateException("sink down");
        }
    }
}
