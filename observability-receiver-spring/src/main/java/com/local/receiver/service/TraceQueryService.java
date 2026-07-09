package com.local.receiver.service;

import com.local.receiver.api.ApiModels.TraceListResponse;
import com.local.receiver.api.ApiModels.TraceSummary;
import com.local.receiver.api.ApiModels.TraceView;
import com.local.receiver.model.SpanExportRecord;
import com.local.receiver.model.SpanRecord;
import com.local.receiver.store.TraceRecordStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class TraceQueryService {
    private final TraceRecordStore store;
    private final long slowThresholdMillis;

    public TraceQueryService(TraceRecordStore store,
                             @Value("${receiver.trace.slow-threshold-millis:500}") long slowThresholdMillis) {
        this.store = store;
        this.slowThresholdMillis = slowThresholdMillis;
    }

    public TraceListResponse list(String endpoint,
                                  String status,
                                  String protocol,
                                  long minDurationMillis,
                                  int limit) {
        int safeLimit = normalizeLimit(limit);
        Map<String, List<SpanExportRecord>> grouped = store.groupByTraceId();
        List<TraceSummary> items = grouped.entrySet().stream()
                .map(entry -> summarize(entry.getKey(), entry.getValue()))
                .filter(summary -> matchesSummary(summary, status, minDurationMillis))
                .filter(summary -> matchesTrace(grouped.get(summary.traceId()), endpoint, protocol))
                .sorted(Comparator.comparingLong(TraceSummary::capturedAtMillis).reversed())
                .limit(safeLimit)
                .toList();
        return new TraceListResponse(items.size(), items);
    }

    public TraceView find(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return null;
        }
        List<SpanExportRecord> exports = store.groupByTraceId().get(traceId);
        if (exports == null || exports.isEmpty()) {
            return null;
        }
        return new TraceView(traceId, summarize(traceId, exports), exports);
    }

    public Map<String, List<SpanExportRecord>> findForEndpoint(String endpoint, int limit) {
        int safeLimit = normalizeLimit(limit);
        return store.groupByTraceId().entrySet().stream()
                .filter(entry -> matchesTrace(entry.getValue(), endpoint, null))
                .sorted((left, right) -> Long.compare(
                        summarize(right.getKey(), right.getValue()).capturedAtMillis(),
                        summarize(left.getKey(), left.getValue()).capturedAtMillis()))
                .limit(safeLimit)
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        java.util.LinkedHashMap::new));
    }

    public Map<String, List<SpanExportRecord>> recentTraceMap(int limit) {
        int safeLimit = normalizeLimit(limit);
        return store.groupByTraceId().entrySet().stream()
                .sorted((left, right) -> Long.compare(
                        summarize(right.getKey(), right.getValue()).capturedAtMillis(),
                        summarize(left.getKey(), left.getValue()).capturedAtMillis()))
                .limit(safeLimit)
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        java.util.LinkedHashMap::new));
    }

    private TraceSummary summarize(String traceId, List<SpanExportRecord> exports) {
        List<SpanRecord> spans = allSpans(exports);
        SpanRecord root = spans.stream()
                .filter(span -> span.parentSpanId() == null || span.parentSpanId().isBlank())
                .min(Comparator.comparingLong(SpanRecord::startEpochMillis))
                .orElseGet(() -> spans.stream()
                        .min(Comparator.comparingLong(SpanRecord::startEpochMillis))
                        .orElse(null));

        long start = spans.stream().mapToLong(SpanRecord::startEpochMillis).filter(value -> value > 0).min().orElse(0);
        long end = spans.stream()
                .mapToLong(span -> span.startEpochMillis() <= 0 ? 0 : span.startEpochMillis() + Math.max(0, span.durationMillis()))
                .max()
                .orElse(0);
        long duration = start > 0 && end >= start ? end - start : spans.stream().mapToLong(SpanRecord::durationMillis).max().orElse(0);
        boolean error = spans.stream().anyMatch(SpanRecord::isError);
        boolean slow = spans.stream().anyMatch(span -> span.durationMillis() >= slowThresholdMillis);
        long capturedAt = exports.stream().mapToLong(SpanExportRecord::capturedAtMillis).max().orElse(0);
        List<String> services = exports.stream()
                .map(SpanExportRecord::serviceName)
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new));

        return new TraceSummary(
                traceId,
                root == null ? "unknown" : root.name(),
                services,
                spans.size(),
                error,
                slow,
                duration,
                capturedAt
        );
    }

    private boolean matchesSummary(TraceSummary summary, String status, long minDurationMillis) {
        if (minDurationMillis > 0 && summary.durationMillis() < minDurationMillis) {
            return false;
        }
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return true;
        }
        if ("error".equalsIgnoreCase(status)) {
            return summary.error();
        }
        if ("slow".equalsIgnoreCase(status)) {
            return summary.slow();
        }
        return true;
    }

    private static boolean matchesTrace(List<SpanExportRecord> exports, String endpoint, String protocol) {
        if (exports == null || exports.isEmpty()) {
            return false;
        }
        return allSpans(exports).stream().anyMatch(span -> matchesSpan(span, endpoint, protocol));
    }

    private static boolean matchesSpan(SpanRecord span, String endpoint, String protocol) {
        if (protocol != null && !protocol.isBlank()) {
            String spanProtocol = span.protocol();
            if (spanProtocol == null || !spanProtocol.equalsIgnoreCase(protocol)) {
                return false;
            }
        }
        if (endpoint == null || endpoint.isBlank()) {
            return true;
        }
        String query = endpoint.toLowerCase();
        if (contains(span.name(), query)) {
            return true;
        }
        return span.attributes().entrySet().stream()
                .anyMatch(entry -> contains(entry.getKey(), query) || contains(entry.getValue(), query));
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase().contains(query);
    }

    private static List<SpanRecord> allSpans(List<SpanExportRecord> exports) {
        return exports.stream()
                .flatMap(export -> export.spans().stream())
                .toList();
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 20;
        }
        return Math.min(limit, 200);
    }
}
