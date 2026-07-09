package com.local.receiver.store;

import com.local.receiver.model.EndpointMetricsRecord;
import com.local.receiver.model.MetricsExportRecord;
import com.local.receiver.model.MetricsSnapshotRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MetricsRecordStore {
    private final BoundedRingBuffer<MetricsExportRecord> records;

    public MetricsRecordStore(@Value("${receiver.store.max-metrics-records:500}") int capacity) {
        this.records = new BoundedRingBuffer<>(capacity);
    }

    // Synchronized is enough for the mini backend. If this becomes a real service, split write/read indexes.
    public synchronized void append(MetricsExportRecord record) {
        records.addFirst(record);
    }

    public synchronized List<MetricsExportRecord> recent() {
        return records.snapshotNewestFirst();
    }

    public synchronized StoreStats stats() {
        return new StoreStats(records.accepted(), records.dropped(), records.size(), records.capacity());
    }

    public synchronized void clear() {
        records.clear();
    }

    public MetricsExportRecord filterRecord(MetricsExportRecord record, String endpoint, String side) {
        if (endpoint == null || endpoint.isBlank()) {
            return record;
        }
        MetricsSnapshotRecord snapshot = record.snapshot();
        Map<String, EndpointMetricsRecord> server = shouldReadServer(side)
                ? filterEndpoints(snapshot.serverEndpoints(), endpoint)
                : Map.of();
        Map<String, EndpointMetricsRecord> client = shouldReadClient(side)
                ? filterEndpoints(snapshot.clientCalls(), endpoint)
                : Map.of();
        Map<String, Long> failures = shouldReadClient(side)
                ? filterLongs(snapshot.consecutiveFailures(), endpoint)
                : Map.of();
        if (server.isEmpty() && client.isEmpty() && failures.isEmpty()) {
            return null;
        }
        MetricsSnapshotRecord filtered = new MetricsSnapshotRecord(
                snapshot.inFlightRequests(),
                server,
                client,
                failures
        );
        return new MetricsExportRecord(record.serviceName(), record.instanceId(), record.capturedAtMillis(), filtered);
    }

    private static Map<String, EndpointMetricsRecord> filterEndpoints(Map<String, EndpointMetricsRecord> source,
                                                                      String endpoint) {
        Map<String, EndpointMetricsRecord> result = new LinkedHashMap<>();
        source.forEach((name, value) -> {
            if (matches(name, endpoint)) {
                result.put(name, value);
            }
        });
        return result;
    }

    private static Map<String, Long> filterLongs(Map<String, Long> source, String endpoint) {
        Map<String, Long> result = new LinkedHashMap<>();
        source.forEach((name, value) -> {
            if (matches(name, endpoint)) {
                result.put(name, value);
            }
        });
        return result;
    }

    private static boolean shouldReadServer(String side) {
        return side == null || side.isBlank() || "all".equalsIgnoreCase(side) || "server".equalsIgnoreCase(side);
    }

    private static boolean shouldReadClient(String side) {
        return side == null || side.isBlank() || "all".equalsIgnoreCase(side) || "client".equalsIgnoreCase(side);
    }

    private static boolean matches(String value, String query) {
        return value != null && value.toLowerCase().contains(query.toLowerCase());
    }
}
