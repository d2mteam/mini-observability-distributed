package com.local.receiver.store;

import com.local.receiver.model.SpanExportRecord;
import com.local.receiver.model.SpanRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TraceRecordStore {
    private final BoundedRingBuffer<SpanExportRecord> records;

    public TraceRecordStore(@Value("${receiver.store.max-span-exports:500}") int capacity) {
        this.records = new BoundedRingBuffer<>(capacity);
    }

    // Demo backend: keep the received SpanExport envelope, and assemble trace views at query time.
    public synchronized void append(SpanExportRecord record) {
        records.addFirst(record);
    }

    public synchronized List<SpanExportRecord> recentExports() {
        return records.snapshotNewestFirst();
    }

    public synchronized Map<String, List<SpanExportRecord>> groupByTraceId() {
        Map<String, List<SpanExportRecord>> grouped = new LinkedHashMap<>();
        for (SpanExportRecord export : records.snapshotNewestFirst()) {
            Map<String, List<SpanRecord>> spansByTrace = new LinkedHashMap<>();
            for (SpanRecord span : export.spans()) {
                if (span.traceId() == null || span.traceId().isBlank()) {
                    continue;
                }
                spansByTrace.computeIfAbsent(span.traceId(), ignored -> new ArrayList<>()).add(span);
            }
            spansByTrace.forEach((traceId, spans) -> grouped
                    .computeIfAbsent(traceId, ignored -> new ArrayList<>())
                    .add(new SpanExportRecord(export.serviceName(), export.instanceId(), export.capturedAtMillis(), spans)));
        }
        return copyGrouped(grouped);
    }

    public synchronized StoreStats stats() {
        return new StoreStats(records.accepted(), records.dropped(), records.size(), records.capacity());
    }

    public synchronized void clear() {
        records.clear();
    }

    private static Map<String, List<SpanExportRecord>> copyGrouped(Map<String, List<SpanExportRecord>> source) {
        Map<String, List<SpanExportRecord>> copy = new LinkedHashMap<>();
        source.forEach((traceId, exports) -> copy.put(traceId, List.copyOf(exports)));
        return Collections.unmodifiableMap(copy);
    }
}
