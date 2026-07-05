package com.core.export.metrics;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

public class HttpExportSink implements ExportSink {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final URI endpoint;
    private final Duration timeout;

    public HttpExportSink(String endpoint) {
        this(URI.create(endpoint));
    }

    public HttpExportSink(URI endpoint) {
        this(HttpClient.newHttpClient(), endpoint, DEFAULT_TIMEOUT);
    }

    public HttpExportSink(HttpClient httpClient, URI endpoint, Duration timeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    @Override
    public void send(String json) {
        Objects.requireNonNull(json, "json");
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("metrics export failed: HTTP " + response.statusCode());
            }
        } catch (IOException e) {
            throw new IllegalStateException("metrics export failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("metrics export interrupted", e);
        }
    }
}
