package com.local.receiver.service;

import com.local.receiver.api.ApiModels.MetricsQueryResponse;
import com.local.receiver.model.MetricsExportRecord;
import com.local.receiver.store.MetricsRecordStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MetricsQueryService {
    private final MetricsRecordStore store;

    public MetricsQueryService(MetricsRecordStore store) {
        this.store = store;
    }

    public MetricsQueryResponse query(String endpoint,
                                      String side,
                                      long fromMillis,
                                      long toMillis,
                                      int limit) {
        int safeLimit = normalizeLimit(limit);
        List<MetricsExportRecord> records = store.recent().stream()
                .filter(record -> inWindow(record.capturedAtMillis(), fromMillis, toMillis))
                .map(record -> store.filterRecord(record, endpoint, side))
                .filter(record -> record != null)
                .limit(safeLimit)
                .toList();
        return new MetricsQueryResponse(endpoint, side == null ? "all" : side, fromMillis, toMillis, records.size(), records);
    }

    public MetricsExportRecord latestForEndpoint(String endpoint) {
        return store.recent().stream()
                .map(record -> store.filterRecord(record, endpoint, "all"))
                .filter(record -> record != null)
                .findFirst()
                .orElse(null);
    }

    public List<MetricsExportRecord> recent(int limit) {
        int safeLimit = normalizeLimit(limit);
        return store.recent().stream()
                .limit(safeLimit)
                .toList();
    }

    private static boolean inWindow(long capturedAtMillis, long fromMillis, long toMillis) {
        return (fromMillis <= 0 || capturedAtMillis >= fromMillis)
                && (toMillis <= 0 || capturedAtMillis <= toMillis);
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 50;
        }
        return Math.min(limit, 500);
    }
}
