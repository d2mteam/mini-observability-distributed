package com.core.export.tracing;

import com.core.tracing.Span;
import lombok.Builder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Builder
public record SpanRecord(String traceId,
                         String spanId,
                         String parentSpanId,
                         String name,
                         Span.Kind kind,
                         long startEpochMillis,
                         long startNanos,
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
        return SpanRecord.builder()
                .traceId(span.getTraceId())
                .spanId(span.getSpanId())
                .parentSpanId(span.getParentSpanId())
                .name(span.getName())
                .kind(span.getKind())
                .startEpochMillis(span.getStartEpochMillis())
                .startNanos(span.getStartNanos())
                .durationMillis(span.getDurationMillis())
                .status(span.getStatus())
                .sampled(span.isSampled())
                .attributes(span.getAttributes())
                .build();
    }

    private static Map<String, String> immutableCopy(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
