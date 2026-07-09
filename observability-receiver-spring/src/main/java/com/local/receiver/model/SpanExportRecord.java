package com.local.receiver.model;

import java.util.List;

public record SpanExportRecord(String serviceName,
                               String instanceId,
                               long capturedAtMillis,
                               List<SpanRecord> spans) {
    public SpanExportRecord {
        spans = spans == null ? List.of() : List.copyOf(spans);
    }
}
