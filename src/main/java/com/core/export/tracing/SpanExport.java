package com.core.export.tracing;

import java.util.List;
import java.util.Objects;

public record SpanExport(String serviceName,
                         String instanceId,
                         long capturedAtMillis,
                         List<SpanRecord> spans) {

    public SpanExport {
        Objects.requireNonNull(serviceName, "serviceName");
        Objects.requireNonNull(instanceId, "instanceId");
        spans = List.copyOf(Objects.requireNonNull(spans, "spans"));
    }
}
