package com.local.receiver.service;

import com.local.receiver.api.ApiModels.AiContextBlock;
import com.local.receiver.api.ApiModels.ErrorPattern;
import com.local.receiver.api.ApiModels.RelatedMetric;
import com.local.receiver.api.ApiModels.StructuredExport;
import com.local.receiver.model.EndpointMetricsRecord;
import com.local.receiver.model.MetricsExportRecord;
import com.local.receiver.model.SpanExportRecord;
import com.local.receiver.model.SpanRecord;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ExportService {
    private static final int MAX_CANDIDATE_TRACES = 100;
    private static final int MAX_ERROR_PATTERNS = 5;
    private static final int MAX_RELATED_METRICS = 10;
    private static final int MAX_METRIC_RECORDS_TO_SCAN = 100;

    private final MetricsQueryService metricsQueryService;
    private final TraceQueryService traceQueryService;

    public ExportService(MetricsQueryService metricsQueryService, TraceQueryService traceQueryService) {
        this.metricsQueryService = metricsQueryService;
        this.traceQueryService = traceQueryService;
    }

    public StructuredExport structuredJson(String endpoint, int metricsLimit, int traceLimit) {
        List<MetricsExportRecord> metrics = metricsQueryService
                .query(endpoint, "all", 0, 0, metricsLimit)
                .metrics();
        return new StructuredExport(metrics, endpoint == null || endpoint.isBlank()
                ? traceQueryService.recentTraceMap(traceLimit)
                : traceQueryService.findForEndpoint(endpoint, traceLimit));
    }

    public AiContextBlock aiContext(String endpoint, int traceLimit) {
        int safeTraceLimit = Math.min(Math.max(traceLimit, 1), 20);
        MetricsExportRecord primaryMetrics = metricsQueryService.latestForEndpoint(endpoint);

        Map<String, List<SpanExportRecord>> traces = selectAffectedTraces(endpoint, safeTraceLimit);
        List<String> traceIds = List.copyOf(traces.keySet());
        List<ErrorPattern> errorPatterns = errorPatterns(traces);

        Map<String, LinkedHashSet<String>> errorMetricKeys = relatedMetricKeys(endpoint, traces, true);
        List<RelatedMetric> relatedMetrics = relatedMetrics(errorMetricKeys, errorMetricKeys.keySet());

        return new AiContextBlock(endpoint, primaryMetrics, errorPatterns, traceIds, relatedMetrics, traces);
    }

    private Map<String, List<SpanExportRecord>> selectAffectedTraces(String endpoint, int traceLimit) {
        return traceQueryService.findForEndpoint(endpoint, MAX_CANDIDATE_TRACES)
                .entrySet()
                .stream()
                .sorted((left, right) -> {
                    int byScore = Integer.compare(traceScore(right.getValue()), traceScore(left.getValue()));
                    if (byScore != 0) {
                        return byScore;
                    }
                    return Long.compare(capturedAt(right.getValue()), capturedAt(left.getValue()));
                })
                .limit(traceLimit)
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    private static int traceScore(List<SpanExportRecord> exports) {
        List<SpanRecord> spans = allSpans(exports);
        int score = 0;
        for (SpanRecord span : spans) {
            if (isRootFailure(span)) {
                score += 50;
            }
            if (isErrorSpan(span)) {
                score += 20;
            }
            if (isHttpErrorStatus(attribute(span, "http.status_code"))) {
                score += 10;
            }
        }
        score += retryLikeGroups(spans) * 5;
        return score;
    }

    private static int retryLikeGroups(List<SpanRecord> spans) {
        Map<String, AttemptGroup> groups = new LinkedHashMap<>();
        for (SpanRecord span : spans) {
            if (!"CLIENT".equalsIgnoreCase(span.kind())) {
                continue;
            }
            String key = retryKey(span);
            groups.computeIfAbsent(key, ignored -> new AttemptGroup()).add(span);
        }
        int groupsWithErrors = 0;
        for (AttemptGroup group : groups.values()) {
            if (group.count > 1 && group.errors > 0) {
                groupsWithErrors++;
            }
        }
        return groupsWithErrors;
    }

    private static String retryKey(SpanRecord span) {
        return normalize(span.name()) + "|" + normalize(span.protocol()) + "|" + normalize(destination(span));
    }

    private List<ErrorPattern> errorPatterns(Map<String, List<SpanExportRecord>> traces) {
        Map<String, PatternAccumulator> patterns = new LinkedHashMap<>();
        traces.forEach((traceId, exports) -> {
            for (SpanExportRecord export : exports) {
                for (SpanRecord span : export.spans()) {
                    if (!isErrorSpan(span)) {
                        continue;
                    }
                    String key = patternKey(export, span);
                    patterns.computeIfAbsent(key, ignored -> new PatternAccumulator(key, export, span))
                            .add(traceId, span);
                }
            }
        });
        return patterns.values()
                .stream()
                .sorted(Comparator.comparingInt(PatternAccumulator::occurrences).reversed()
                        .thenComparing(PatternAccumulator::key))
                .limit(MAX_ERROR_PATTERNS)
                .map(PatternAccumulator::toPattern)
                .toList();
    }

    private List<RelatedMetric> relatedMetrics(Map<String, LinkedHashSet<String>> metricKeys,
                                               Set<String> errorMetricKeys) {
        if (metricKeys.isEmpty()) {
            return List.of();
        }
        List<RelatedMetric> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (MetricsExportRecord export : metricsQueryService.recent(MAX_METRIC_RECORDS_TO_SCAN)) {
            addEndpointMetrics(result, seen, export, "serverEndpoints",
                    export.snapshot().serverEndpoints(), export.snapshot().consecutiveFailures(), metricKeys, errorMetricKeys);
            addEndpointMetrics(result, seen, export, "clientCalls",
                    export.snapshot().clientCalls(), export.snapshot().consecutiveFailures(), metricKeys, errorMetricKeys);
            addFailureOnlyMetrics(result, seen, export, metricKeys, errorMetricKeys);
            if (result.size() >= MAX_RELATED_METRICS) {
                return List.copyOf(result.subList(0, MAX_RELATED_METRICS));
            }
        }
        return List.copyOf(result);
    }

    private static void addEndpointMetrics(List<RelatedMetric> result,
                                           Set<String> seen,
                                           MetricsExportRecord export,
                                           String side,
                                           Map<String, EndpointMetricsRecord> endpoints,
                                           Map<String, Long> consecutiveFailures,
                                           Map<String, LinkedHashSet<String>> metricKeys,
                                           Set<String> errorMetricKeys) {
        endpoints.forEach((key, value) -> {
            LinkedHashSet<String> traceIds = traceIdsForMetric(key, metricKeys);
            if (traceIds.isEmpty() || !shouldIncludeMetric(key, value, consecutiveFailures.get(key), errorMetricKeys)) {
                return;
            }
            String id = export.serviceName() + "|" + export.instanceId() + "|" + side + "|" + key;
            if (seen.add(id)) {
                result.add(new RelatedMetric(
                        export.serviceName(),
                        export.instanceId(),
                        export.capturedAtMillis(),
                        side,
                        key,
                        value,
                        consecutiveFailures.get(key),
                        List.copyOf(traceIds)));
            }
        });
    }

    private static void addFailureOnlyMetrics(List<RelatedMetric> result,
                                              Set<String> seen,
                                              MetricsExportRecord export,
                                              Map<String, LinkedHashSet<String>> metricKeys,
                                              Set<String> errorMetricKeys) {
        export.snapshot().consecutiveFailures().forEach((key, value) -> {
            LinkedHashSet<String> traceIds = traceIdsForMetric(key, metricKeys);
            if (traceIds.isEmpty() || value <= 0 && !matchesAny(key, errorMetricKeys)) {
                return;
            }
            String id = export.serviceName() + "|" + export.instanceId() + "|consecutiveFailures|" + key;
            if (seen.add(id)) {
                result.add(new RelatedMetric(
                        export.serviceName(),
                        export.instanceId(),
                        export.capturedAtMillis(),
                        "consecutiveFailures",
                        key,
                        null,
                        value,
                        List.copyOf(traceIds)));
            }
        });
    }

    private static boolean shouldIncludeMetric(String key,
                                               EndpointMetricsRecord value,
                                               Long consecutiveFailures,
                                               Set<String> errorMetricKeys) {
        return value.errors() > 0
                || (consecutiveFailures != null && consecutiveFailures > 0)
                || matchesAny(key, errorMetricKeys);
    }

    private static LinkedHashSet<String> traceIdsForMetric(String metricKey,
                                                           Map<String, LinkedHashSet<String>> metricKeys) {
        LinkedHashSet<String> traceIds = new LinkedHashSet<>();
        metricKeys.forEach((key, values) -> {
            if (matches(metricKey, key)) {
                traceIds.addAll(values);
            }
        });
        return traceIds;
    }

    private static Map<String, LinkedHashSet<String>> relatedMetricKeys(String endpoint,
                                                                        Map<String, List<SpanExportRecord>> traces,
                                                                        boolean onlyErrorSpans) {
        Map<String, LinkedHashSet<String>> keys = new LinkedHashMap<>();
        traces.forEach((traceId, exports) -> {
            for (SpanRecord span : allSpans(exports)) {
                if (onlyErrorSpans && !isErrorSpan(span)) {
                    continue;
                }
                for (String key : metricKeys(span)) {
                    if (!isPrimaryEndpoint(endpoint, key)) {
                        keys.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(traceId);
                    }
                }
            }
        });
        return keys;
    }

    private static List<String> metricKeys(SpanRecord span) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        addIfPresent(keys, attribute(span, "server.address"));
        addIfPresent(keys, attribute(span, "db.system"));
        addIfPresent(keys, attribute(span, "rsocket.route"));
        addIfPresent(keys, attribute(span, "messaging.destination"));
        addIfPresent(keys, routeFromSpanName(span.name()));
        return List.copyOf(keys);
    }

    private static void addIfPresent(Set<String> keys, String value) {
        if (value != null && !value.isBlank()) {
            keys.add(value);
        }
    }

    private static boolean isPrimaryEndpoint(String endpoint, String key) {
        return endpoint != null
                && !endpoint.isBlank()
                && key != null
                && (endpoint.equalsIgnoreCase(key) || matches(endpoint, key));
    }

    private static boolean matchesAny(String metricKey, Set<String> keys) {
        return keys.stream().anyMatch(key -> matches(metricKey, key));
    }

    private static boolean matches(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        String a = left.toLowerCase(Locale.ROOT);
        String b = right.toLowerCase(Locale.ROOT);
        return a.equals(b) || a.contains(b) || b.contains(a);
    }

    private static boolean isRootFailure(SpanRecord span) {
        return "SERVER".equalsIgnoreCase(span.kind())
                && (span.parentSpanId() == null || span.parentSpanId().isBlank())
                && isErrorSpan(span);
    }

    private static boolean isErrorSpan(SpanRecord span) {
        return span.isError()
                || "ERROR".equalsIgnoreCase(attribute(span, "mini.status"))
                || hasText(attribute(span, "error"))
                || hasText(attribute(span, "error.type"))
                || hasText(attribute(span, "error.message"))
                || isHttpErrorStatus(attribute(span, "http.status_code"));
    }

    private static boolean isHttpErrorStatus(String value) {
        try {
            return value != null && Integer.parseInt(value) >= 400;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String patternKey(SpanExportRecord export, SpanRecord span) {
        return normalize(export.serviceName())
                + "|" + normalize(span.name())
                + "|" + normalize(span.kind())
                + "|" + normalize(span.protocol())
                + "|" + normalize(errorValue(span))
                + "|" + normalize(attribute(span, "http.status_code"))
                + "|" + normalize(attribute(span, "db.operation"))
                + "|" + normalize(destination(span));
    }

    private static String errorValue(SpanRecord span) {
        String error = attribute(span, "error");
        if (hasText(error)) {
            return error;
        }
        error = attribute(span, "error.type");
        if (hasText(error)) {
            return error;
        }
        return attribute(span, "error.message");
    }

    private static String destination(SpanRecord span) {
        String value = attribute(span, "server.address");
        if (hasText(value)) {
            return value;
        }
        value = attribute(span, "db.system");
        if (hasText(value)) {
            return value;
        }
        value = attribute(span, "rsocket.route");
        if (hasText(value)) {
            return value;
        }
        value = attribute(span, "messaging.destination");
        if (hasText(value)) {
            return value;
        }
        return routeFromSpanName(span.name());
    }

    private static String routeFromSpanName(String spanName) {
        if (spanName == null || spanName.isBlank()) {
            return null;
        }
        String trimmed = spanName.trim();
        int index = trimmed.indexOf(" /");
        if (index < 0) {
            return null;
        }
        return trimmed.substring(index + 1).trim();
    }

    private static String attribute(SpanRecord span, String key) {
        return span.attributes().get(key);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static long capturedAt(List<SpanExportRecord> exports) {
        return exports.stream().mapToLong(SpanExportRecord::capturedAtMillis).max().orElse(0);
    }

    private static List<SpanRecord> allSpans(List<SpanExportRecord> exports) {
        return exports.stream()
                .flatMap(export -> export.spans().stream())
                .toList();
    }

    private static final class PatternAccumulator {
        private final String key;
        private final String serviceName;
        private final String spanName;
        private final String kind;
        private final String protocol;
        private final String status;
        private final String error;
        private final String httpStatusCode;
        private final String dbOperation;
        private final String destination;
        private final LinkedHashSet<String> traceIds = new LinkedHashSet<>();
        private final LinkedHashSet<String> spanIds = new LinkedHashSet<>();
        private int occurrences;

        private PatternAccumulator(String key, SpanExportRecord export, SpanRecord span) {
            this.key = key;
            this.serviceName = export.serviceName();
            this.spanName = span.name();
            this.kind = span.kind();
            this.protocol = span.protocol();
            this.status = span.status();
            this.error = errorValue(span);
            this.httpStatusCode = attribute(span, "http.status_code");
            this.dbOperation = attribute(span, "db.operation");
            this.destination = destination(span);
        }

        private void add(String traceId, SpanRecord span) {
            occurrences++;
            traceIds.add(traceId);
            spanIds.add(span.spanId());
        }

        private int occurrences() {
            return occurrences;
        }

        private String key() {
            return key;
        }

        private ErrorPattern toPattern() {
            return new ErrorPattern(
                    key,
                    serviceName,
                    spanName,
                    kind,
                    protocol,
                    status,
                    error,
                    httpStatusCode,
                    dbOperation,
                    destination,
                    occurrences,
                    List.copyOf(traceIds),
                    List.copyOf(spanIds)
            );
        }
    }

    private static final class AttemptGroup {
        private int count;
        private int errors;

        private void add(SpanRecord span) {
            count++;
            if (isErrorSpan(span)) {
                errors++;
            }
        }
    }
}
