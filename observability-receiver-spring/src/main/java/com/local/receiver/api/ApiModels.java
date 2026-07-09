package com.local.receiver.api;

import com.local.receiver.model.MetricsExportRecord;
import com.local.receiver.model.EndpointMetricsRecord;
import com.local.receiver.model.SpanExportRecord;
import com.local.receiver.store.StoreStats;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ApiModels {
    private ApiModels() {
    }

    public record IngestResponse(boolean accepted,
                                 String type,
                                 int records,
                                 int spans,
                                 List<String> traceIds) {
        public IngestResponse {
            traceIds = traceIds == null ? List.of() : List.copyOf(traceIds);
        }
    }

    public record BackendStats(StoreStats metrics,
                               StoreStats traces) {
    }

    public record MetricsQueryResponse(String endpoint,
                                       String side,
                                       long fromMillis,
                                       long toMillis,
                                       int records,
                                       List<MetricsExportRecord> metrics) {
        public MetricsQueryResponse {
            metrics = metrics == null ? List.of() : List.copyOf(metrics);
        }
    }

    public record TraceSummary(String traceId,
                               String rootName,
                               List<String> serviceNames,
                               int spanCount,
                               boolean error,
                               boolean slow,
                               long durationMillis,
                               long capturedAtMillis) {
        public TraceSummary {
            serviceNames = serviceNames == null ? List.of() : List.copyOf(serviceNames);
        }
    }

    public record TraceListResponse(int traces,
                                    List<TraceSummary> items) {
        public TraceListResponse {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record TraceView(String traceId,
                            TraceSummary summary,
                            List<SpanExportRecord> exports) {
        public TraceView {
            exports = exports == null ? List.of() : List.copyOf(exports);
        }
    }

    public record StructuredExport(List<MetricsExportRecord> metrics,
                                   Map<String, List<SpanExportRecord>> traces) {
        public StructuredExport {
            metrics = metrics == null ? List.of() : List.copyOf(metrics);
            traces = copyTraceMap(traces);
        }
    }

    public record AiContextBlock(String endpoint,
                                 MetricsExportRecord primaryMetrics,
                                 List<ErrorPattern> errorPatterns,
                                 List<String> traceIds,
                                 List<RelatedMetric> relatedMetrics,
                                 Map<String, List<SpanExportRecord>> traces) {
        public AiContextBlock {
            errorPatterns = errorPatterns == null ? List.of() : List.copyOf(errorPatterns);
            traceIds = traceIds == null ? List.of() : List.copyOf(traceIds);
            relatedMetrics = relatedMetrics == null ? List.of() : List.copyOf(relatedMetrics);
            traces = copyTraceMap(traces);
        }
    }

    public record ErrorPattern(String key,
                               String serviceName,
                               String spanName,
                               String kind,
                               String protocol,
                               String status,
                               String error,
                               String httpStatusCode,
                               String dbOperation,
                               String destination,
                               int occurrences,
                               List<String> traceIds,
                               List<String> spanIds) {
        public ErrorPattern {
            traceIds = traceIds == null ? List.of() : List.copyOf(traceIds);
            spanIds = spanIds == null ? List.of() : List.copyOf(spanIds);
        }
    }

    public record RelatedMetric(String serviceName,
                                String instanceId,
                                long capturedAtMillis,
                                String side,
                                String key,
                                EndpointMetricsRecord endpoint,
                                Long consecutiveFailures,
                                List<String> traceIds) {
        public RelatedMetric {
            traceIds = traceIds == null ? List.of() : List.copyOf(traceIds);
        }
    }

    private static Map<String, List<SpanExportRecord>> copyTraceMap(Map<String, List<SpanExportRecord>> traces) {
        if (traces == null || traces.isEmpty()) {
            return Map.of();
        }
        Map<String, List<SpanExportRecord>> copy = new LinkedHashMap<>();
        traces.forEach((traceId, exports) -> copy.put(traceId, exports == null ? List.of() : List.copyOf(exports)));
        return Collections.unmodifiableMap(copy);
    }
}
