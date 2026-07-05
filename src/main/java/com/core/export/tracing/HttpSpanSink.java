package com.core.export.tracing;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

public class HttpSpanSink implements SpanSink {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final URI endpoint;
    private final Duration timeout;

    public HttpSpanSink(String endpoint) {
        this(URI.create(endpoint));
    }

    public HttpSpanSink(URI endpoint) {
        this(HttpClient.newHttpClient(), endpoint, DEFAULT_TIMEOUT);
    }

    public HttpSpanSink(HttpClient httpClient, URI endpoint, Duration timeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    @Override
    public void send(String json) throws Exception {
        Objects.requireNonNull(json, "json");
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("span export failed: HTTP " + response.statusCode());
        }
    }
}
