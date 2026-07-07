package com.core.export.metrics;

import com.core.metrics.MetricsSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class ElasticsearchMetricsExportSink implements MetricsExportSink {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final URI bulkEndpoint;
    private final String indexName;
    private final Duration timeout;

    public ElasticsearchMetricsExportSink(String bulkEndpoint, String indexName) {
        this(URI.create(bulkEndpoint), indexName);
    }

    public ElasticsearchMetricsExportSink(URI bulkEndpoint, String indexName) {
        this(HttpClient.newHttpClient(), bulkEndpoint, indexName, DEFAULT_TIMEOUT);
    }

    public ElasticsearchMetricsExportSink(HttpClient httpClient, URI bulkEndpoint, String indexName, Duration timeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.bulkEndpoint = Objects.requireNonNull(bulkEndpoint, "bulkEndpoint");
        this.indexName = requireText(indexName, "indexName");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    @Override
    public void send(MetricsExport metricsExport) throws Exception {
        Objects.requireNonNull(metricsExport, "metricsExport");
        // Demo sink: direct Bulk API push only. Add auth/retry/template/per-item error parsing later if needed.
        String ndjson = toBulkNdjson(metricsExport);
        if (ndjson.isEmpty()) {
            return;
        }

        HttpRequest request = HttpRequest.newBuilder(bulkEndpoint)
                .timeout(timeout)
                .header("Content-Type", "application/x-ndjson")
                .POST(HttpRequest.BodyPublishers.ofString(ndjson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("elasticsearch metrics export failed: HTTP " + response.statusCode());
        }
    }

    private String toBulkNdjson(MetricsExport export) throws Exception {
        MetricsSnapshot snapshot = export.snapshot() == null ? MetricsSnapshot.empty() : export.snapshot();
        StringBuilder out = new StringBuilder();

        appendDocument(out, inFlightDocument(export, snapshot.inFlightRequests()));
        for (Map.Entry<String, MetricsSnapshot.Endpoint> entry : snapshot.serverEndpoints().entrySet()) {
            appendDocument(out, serverEndpointDocument(export, entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<String, MetricsSnapshot.Endpoint> entry : snapshot.clientCalls().entrySet()) {
            long consecutiveFailures = snapshot.consecutiveFailures().getOrDefault(entry.getKey(), 0L);
            appendDocument(out, clientCallDocument(export, entry.getKey(), entry.getValue(), consecutiveFailures));
        }
        for (Map.Entry<String, Long> entry : snapshot.consecutiveFailures().entrySet()) {
            if (!snapshot.clientCalls().containsKey(entry.getKey())) {
                appendDocument(out, clientFailureDocument(export, entry.getKey(), entry.getValue()));
            }
        }
        return out.toString();
    }

    private void appendDocument(StringBuilder out, Map<String, Object> document) throws Exception {
        out.append(mapper.writeValueAsString(indexAction(indexName))).append('\n');
        out.append(mapper.writeValueAsString(document)).append('\n');
    }

    private static Map<String, Object> inFlightDocument(MetricsExport export, long inFlightRequests) {
        Map<String, Object> doc = baseDocument(export, "in_flight");
        doc.put("mini_metric", Map.of(
                "type", "in_flight",
                "in_flight_requests", inFlightRequests));
        return doc;
    }

    private static Map<String, Object> serverEndpointDocument(MetricsExport export,
                                                              String route,
                                                              MetricsSnapshot.Endpoint endpoint) {
        MetricsSnapshot.Endpoint safe = endpoint == null ? emptyEndpoint() : endpoint;
        Map<String, Object> metric = endpointFields(safe);
        metric.put("type", "server_endpoint");
        metric.put("route", route);
        metric.put("active_connections", safe.activeConnections());

        Map<String, Object> doc = baseDocument(export, "server_endpoint");
        doc.put("mini_metric", metric);
        return doc;
    }

    private static Map<String, Object> clientCallDocument(MetricsExport export,
                                                          String destination,
                                                          MetricsSnapshot.Endpoint endpoint,
                                                          long consecutiveFailures) {
        MetricsSnapshot.Endpoint safe = endpoint == null ? emptyEndpoint() : endpoint;
        Map<String, Object> metric = endpointFields(safe);
        metric.put("type", "client_call");
        metric.put("destination", destination);
        metric.put("consecutive_failures", consecutiveFailures);

        Map<String, Object> doc = baseDocument(export, "client_call");
        doc.put("mini_metric", metric);
        return doc;
    }

    private static Map<String, Object> clientFailureDocument(MetricsExport export,
                                                             String destination,
                                                             long consecutiveFailures) {
        Map<String, Object> doc = baseDocument(export, "client_failure");
        doc.put("mini_metric", Map.of(
                "type", "client_failure",
                "destination", destination,
                "consecutive_failures", consecutiveFailures));
        return doc;
    }

    private static Map<String, Object> endpointFields(MetricsSnapshot.Endpoint endpoint) {
        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("count", endpoint.count());
        metric.put("errors", endpoint.errors());
        metric.put("slow", endpoint.slow());
        metric.put("total_bytes", endpoint.totalBytes());
        metric.put("error_rate_percent", endpoint.errorRatePercent());
        metric.put("latency_p50_millis", endpoint.p50Millis());
        metric.put("latency_p95_millis", endpoint.p95Millis());
        metric.put("latency_p99_millis", endpoint.p99Millis());
        return metric;
    }

    private static Map<String, Object> baseDocument(MetricsExport export, String dataset) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("@timestamp", Instant.ofEpochMilli(Math.max(0, export.capturedAtMillis())).toString());
        doc.put("service", service(export.serviceName(), export.instanceId()));
        doc.put("data_stream", Map.of("type", "metrics", "dataset", "mini." + dataset));
        doc.put("event", Map.of("kind", "metric"));
        return doc;
    }

    private static Map<String, Object> service(String serviceName, String instanceId) {
        Map<String, Object> service = new LinkedHashMap<>();
        service.put("name", serviceName);
        if (instanceId != null && !instanceId.isBlank()) {
            service.put("node", Map.of("name", instanceId));
        }
        return service;
    }

    private static Map<String, Object> indexAction(String indexName) {
        return Map.of("index", Map.of("_index", indexName));
    }

    private static MetricsSnapshot.Endpoint emptyEndpoint() {
        return new MetricsSnapshot.Endpoint(0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
