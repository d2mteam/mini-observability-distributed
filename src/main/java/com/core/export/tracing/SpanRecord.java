package com.core.export.tracing;

import com.core.tracing.Span;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record SpanRecord(String traceId,
                         String spanId,
                         String parentSpanId,
                         String name,
                         Span.Kind kind,
                         long startEpochMillis,
                         long durationMillis,
                         Span.Status status,
                         boolean sampled,
                         Map<String, String> attributes) {

    public SpanRecord {
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(spanId, "spanId");
        attributes = immutableCopy(attributes);
    }

    public static SpanRecord from(Span span) {
        Objects.requireNonNull(span, "span");
        return new SpanRecord(
                span.getTraceId(),
                span.getSpanId(),
                span.getParentSpanId(),
                span.getName(),
                span.getKind(),
                span.getStartEpochMillis(),
                span.getDurationMillis(),
                span.getStatus(),
                span.isSampled(),
                span.getAttributes());
    }

    private static Map<String, String> immutableCopy(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
