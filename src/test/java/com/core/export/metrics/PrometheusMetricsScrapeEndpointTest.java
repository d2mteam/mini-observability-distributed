package com.core.export.metrics;

import com.core.export.ServiceIdentity;
import com.core.metrics.SimpleMetricsRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrometheusMetricsScrapeEndpointTest {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private PrometheusMetricsScrapeEndpoint endpoint;

    @AfterEach
    void closeEndpoint() throws Exception {
        if (endpoint != null) {
            endpoint.close();
        }
    }

    @Test
    void getMetricsReturnsPrometheusText() throws Exception {
        SimpleMetricsRegistry metrics = new SimpleMetricsRegistry();
        metrics.onServerRequestStart();
        metrics.onServerRequestEnd("GET /orders", 12, false, 100);
        int port = freePort();
        endpoint = new PrometheusMetricsScrapeEndpoint(
                new ServiceIdentity("orders", "instance-1"),
                metrics,
                "127.0.0.1",
                port,
                "/metrics");

        endpoint.start();

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/metrics")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals(PrometheusMetricsScrapeEndpoint.CONTENT_TYPE, response.headers().firstValue("Content-Type").orElse(""));
        assertTrue(response.body().contains("mini_server_requests_total{service=\"orders\",instance=\"instance-1\",route=\"GET /orders\"} 1\n"));
    }

    @Test
    void nonGetMetricsRequestReturnsMethodNotAllowed() throws Exception {
        int port = freePort();
        endpoint = new PrometheusMetricsScrapeEndpoint(
                new ServiceIdentity("orders", "instance-1"),
                new SimpleMetricsRegistry(),
                "127.0.0.1",
                port,
                "/metrics");

        endpoint.start();

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/metrics"))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(405, response.statusCode());
        assertEquals("GET", response.headers().firstValue("Allow").orElse(""));
    }

    @Test
    void closeBeforeStartIsHarmless() throws Exception {
        endpoint = new PrometheusMetricsScrapeEndpoint(
                new ServiceIdentity("orders", "instance-1"),
                new SimpleMetricsRegistry(),
                "127.0.0.1",
                freePort(),
                "/metrics");

        endpoint.close();
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
