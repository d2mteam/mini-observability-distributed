package com.core.export.tracing;

import com.core.tracing.Span;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ZipkinSpanSink implements SpanSink {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    // Zipkin v2 uses microseconds. SpanRecord keeps time fields in milliseconds in this mini project.
    private static final long MICROS_PER_MILLI = 1_000L;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final URI endpoint;
    private final Duration timeout;

    public ZipkinSpanSink(String endpoint) {
        this(URI.create(endpoint));
    }

    public ZipkinSpanSink(URI endpoint) {
        this(HttpClient.newHttpClient(), endpoint, DEFAULT_TIMEOUT);
    }

    public ZipkinSpanSink(HttpClient httpClient, URI endpoint, Duration timeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    @Override
    public void send(SpanExport spanExport) throws Exception {
        Objects.requireNonNull(spanExport, "spanExport");
        String json = mapper.writeValueAsString(toZipkinSpans(spanExport));
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("zipkin span export failed: HTTP " + response.statusCode());
        }
    }

    private static List<ZipkinSpan> toZipkinSpans(SpanExport spanExport) {
        List<ZipkinSpan> spans = new ArrayList<>(spanExport.spans().size());
        for (SpanRecord span : spanExport.spans()) {
            spans.add(toZipkinSpan(spanExport, span));
        }
        return spans;
    }

    private static ZipkinSpan toZipkinSpan(SpanExport export, SpanRecord span) {
        return new ZipkinSpan(
                span.traceId(),
                span.spanId(),
                span.parentSpanId(),
                zipkinKind(span.kind()),
                zipkinName(span),
                timestampMicros(span),
                durationMicros(span),
                localEndpoint(export),
                tags(export, span)
        );
    }

    private static String zipkinName(SpanRecord span) {
        return span.name() == null || span.name().isBlank() ? "unknown" : span.name();
    }

    private static Long timestampMicros(SpanRecord span) {
        if (span.startEpochMillis() <= 0) {
            return null;
        }
        return span.startEpochMillis() * MICROS_PER_MILLI;
    }

    private static Long durationMicros(SpanRecord span) {
        if (span.durationMillis() <= 0) {
            return null;
        }
        return span.durationMillis() * MICROS_PER_MILLI;
    }

    private static ZipkinEndpoint localEndpoint(SpanExport export) {
        return new ZipkinEndpoint(export.serviceName());
    }

    private static String zipkinKind(Span.Kind kind) {
        return kind == null ? null : kind.name();
    }

    private static Map<String, String> tags(SpanExport export, SpanRecord span) {
        Map<String, String> tags = new LinkedHashMap<>(span.attributes());
        if (span.status() != null) {
            tags.put("mini.status", span.status().name());
        }
        tags.put("mini.sampled", String.valueOf(span.sampled()));
        tags.put("mini.instance_id", export.instanceId());
        if (span.status() == Span.Status.ERROR && !tags.containsKey("error")) {
            tags.put("error", "true");
        }
        return tags;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ZipkinSpan(String traceId,
                              String id,
                              String parentId,
                              String kind,
                              String name,
                              Long timestamp,
                              Long duration,
                              ZipkinEndpoint localEndpoint,
                              Map<String, String> tags) {
    }

    private record ZipkinEndpoint(String serviceName) {
    }
}
