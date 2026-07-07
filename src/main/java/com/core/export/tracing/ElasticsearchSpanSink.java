package com.core.export.tracing;

import com.core.tracing.Span;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ElasticsearchSpanSink implements SpanSink {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final URI bulkEndpoint;
    private final String indexName;
    private final Duration timeout;

    public ElasticsearchSpanSink(String bulkEndpoint, String indexName) {
        this(URI.create(bulkEndpoint), indexName);
    }

    public ElasticsearchSpanSink(URI bulkEndpoint, String indexName) {
        this(HttpClient.newHttpClient(), bulkEndpoint, indexName, DEFAULT_TIMEOUT);
    }

    public ElasticsearchSpanSink(HttpClient httpClient, URI bulkEndpoint, String indexName, Duration timeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.bulkEndpoint = Objects.requireNonNull(bulkEndpoint, "bulkEndpoint");
        this.indexName = requireText(indexName, "indexName");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    @Override
    public void send(SpanExport spanExport) throws Exception {
        Objects.requireNonNull(spanExport, "spanExport");
        if (spanExport.spans().isEmpty()) {
            return;
        }

        // Demo sink: direct Bulk API push only. Add auth/retry/template/per-item error parsing later if needed.
        String ndjson = toBulkNdjson(spanExport);
        HttpRequest request = HttpRequest.newBuilder(bulkEndpoint)
                .timeout(timeout)
                .header("Content-Type", "application/x-ndjson")
                .POST(HttpRequest.BodyPublishers.ofString(ndjson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("elasticsearch span export failed: HTTP " + response.statusCode());
        }
    }

    private String toBulkNdjson(SpanExport export) throws Exception {
        StringBuilder out = new StringBuilder();
        for (SpanRecord span : export.spans()) {
            out.append(mapper.writeValueAsString(indexAction(indexName, span.traceId() + "-" + span.spanId()))).append('\n');
            out.append(mapper.writeValueAsString(spanDocument(export, span))).append('\n');
        }
        return out.toString();
    }

    private static Map<String, Object> spanDocument(SpanExport export, SpanRecord span) {
        Map<String, Object> doc = baseDocument(export, "tracing", timestamp(span.startEpochMillis(), export.capturedAtMillis()));
        doc.put("trace", Map.of("id", span.traceId()));
        doc.put("span", spanObject(span));
        if (span.parentSpanId() != null && !span.parentSpanId().isBlank()) {
            doc.put("parent", Map.of("id", span.parentSpanId()));
        }
        doc.put("event", event(span.status()));

        Map<String, Object> mini = new LinkedHashMap<>();
        mini.put("kind", value(span.kind()));
        mini.put("status", value(span.status()));
        mini.put("sampled", span.sampled());
        mini.put("duration_millis", span.durationMillis());
        mini.put("start_epoch_millis", span.startEpochMillis());
        mini.put("start_nanos", span.startNanos());
        mini.put("instance_id", export.instanceId());
        mini.put("attributes", span.attributes());
        doc.put("mini_span", mini);
        return doc;
    }

    private static Map<String, Object> spanObject(SpanRecord span) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", span.spanId());
        if (span.name() != null && !span.name().isBlank()) {
            value.put("name", span.name());
        }
        return value;
    }

    private static Map<String, Object> event(Span.Status status) {
        String type = status == Span.Status.ERROR ? "error" : "info";
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("kind", "event");
        event.put("category", List.of("tracing"));
        event.put("type", List.of(type));
        return event;
    }

    private static Map<String, Object> baseDocument(SpanExport export, String dataset, String timestamp) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("@timestamp", timestamp);
        doc.put("service", service(export.serviceName(), export.instanceId()));
        doc.put("data_stream", Map.of("type", "logs", "dataset", "mini." + dataset));
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

    private static Map<String, Object> indexAction(String indexName, String id) {
        return Map.of("index", Map.of("_index", indexName, "_id", id));
    }

    private static String timestamp(long preferredEpochMillis, long fallbackEpochMillis) {
        long epochMillis = preferredEpochMillis > 0 ? preferredEpochMillis : fallbackEpochMillis;
        return Instant.ofEpochMilli(Math.max(0, epochMillis)).toString();
    }

    private static String value(Object value) {
        return value == null ? null : value.toString();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
