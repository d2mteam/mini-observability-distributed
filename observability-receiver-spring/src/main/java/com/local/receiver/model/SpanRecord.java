package com.local.receiver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.LinkedHashMap;
import java.util.Map;

public record SpanRecord(String traceId,
                         String spanId,
                         String parentSpanId,
                         String name,
                         String kind,
                         long startEpochMillis,
                         long startNanos,
                         long durationMillis,
                         String status,
                         boolean sampled,
                         Map<String, String> attributes) {
    public SpanRecord {
        attributes = copy(attributes);
    }

    @JsonIgnore
    public boolean isError() {
        return "ERROR".equalsIgnoreCase(status);
    }

    @JsonIgnore
    public String protocol() {
        return attributes.get("protocol");
    }

    private static Map<String, String> copy(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(source));
    }
}
