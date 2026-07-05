package com.core.tracing;

import com.core.export.tracing.SpanRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpanRecordTest {

    @Test
    void snapshotCopiesFinishedSpanData() {
        Span span = Span.builder()
                .traceId("0af7651916cd43dd8448eb211c80319c")
                .spanId("b7ad6b7169203331")
                .parentSpanId("1111111111111111")
                .sampled(true)
                .startEpochMillis(1_000)
                .startNanos(0)
                .build()
                .name("GET /orders/{id}")
                .kind(Span.Kind.SERVER)
                .tag("http.status_code", "200");
        span.end(25_000_000);

        SpanRecord record = SpanRecord.from(span);

        assertEquals(span.getTraceId(), record.traceId());
        assertEquals(span.getSpanId(), record.spanId());
        assertEquals(span.getParentSpanId(), record.parentSpanId());
        assertEquals("GET /orders/{id}", record.name());
        assertEquals(Span.Kind.SERVER, record.kind());
        assertEquals(1_000, record.startEpochMillis());
        assertEquals(25, record.durationMillis());
        assertEquals(Span.Status.OK, record.status());
        assertEquals(true, record.sampled());
        assertEquals("200", record.attributes().get("http.status_code"));
    }

    @Test
    void snapshotDoesNotKeepMutableAttributeReference() {
        Span span = Span.builder()
                .traceId("0af7651916cd43dd8448eb211c80319c")
                .spanId("b7ad6b7169203331")
                .sampled(true)
                .startEpochMillis(1_000)
                .build()
                .tag("key", "before");
        span.end(1_001);

        SpanRecord record = SpanRecord.from(span);

        span.getAttributes().put("key", "after");
        span.getAttributes().put("new", "value");

        assertEquals("before", record.attributes().get("key"));
        assertEquals(false, record.attributes().containsKey("new"));
        assertThrows(UnsupportedOperationException.class,
                () -> record.attributes().put("another", "value"));
    }
}
