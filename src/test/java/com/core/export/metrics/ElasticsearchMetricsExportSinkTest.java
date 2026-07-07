package com.core.export.metrics;

import com.core.metrics.MetricsSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticsearchMetricsExportSinkTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<CapturedRequest> captured = new ArrayList<>();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postsMetricDocumentsAsBulkNdjson() throws Exception {
        URI endpoint = startServer(200);
        ElasticsearchMetricsExportSink sink = new ElasticsearchMetricsExportSink(endpoint, "mini-metrics");

        sink.send(metricsExport());

        assertEquals(1, captured.size());
        CapturedRequest request = captured.getFirst();
        assertEquals("POST", request.method());
        assertEquals("/_bulk", request.path());
        assertEquals("application/x-ndjson", request.contentType());
        assertTrue(request.body().endsWith("\n"));

        String[] lines = request.body().split("\n");
        assertEquals(8, lines.length);

        JsonNode firstAction = MAPPER.readTree(lines[0]);
        assertEquals("mini-metrics", firstAction.get("index").get("_index").asText());

        JsonNode inFlight = MAPPER.readTree(lines[1]);
        assertEquals("2026-01-01T00:00:00Z", inFlight.get("@timestamp").asText());
        assertEquals("orders", inFlight.get("service").get("name").asText());
        assertEquals("metric", inFlight.get("event").get("kind").asText());
        assertEquals("in_flight", inFlight.get("mini_metric").get("type").asText());
        assertEquals(2, inFlight.get("mini_metric").get("in_flight_requests").asLong());

        JsonNode server = MAPPER.readTree(lines[3]);
        assertEquals("server_endpoint", server.get("mini_metric").get("type").asText());
        assertEquals("GET /orders/{id}", server.get("mini_metric").get("route").asText());
        assertEquals(100, server.get("mini_metric").get("count").asLong());
        assertEquals(3, server.get("mini_metric").get("active_connections").asLong());

        JsonNode client = MAPPER.readTree(lines[5]);
        assertEquals("client_call", client.get("mini_metric").get("type").asText());
        assertEquals("inventory:8080", client.get("mini_metric").get("destination").asText());
        assertEquals(4, client.get("mini_metric").get("consecutive_failures").asLong());

        JsonNode failureOnly = MAPPER.readTree(lines[7]);
        assertEquals("client_failure", failureOnly.get("mini_metric").get("type").asText());
        assertEquals("payments:8080", failureOnly.get("mini_metric").get("destination").asText());
    }

    @Test
    void throwsWhenServerReturnsNon2xx() throws Exception {
        URI endpoint = startServer(500);
        ElasticsearchMetricsExportSink sink = new ElasticsearchMetricsExportSink(endpoint, "mini-metrics");

        assertThrows(IllegalStateException.class, () -> sink.send(metricsExport()));
    }

    private static MetricsExport metricsExport() {
        Map<String, MetricsSnapshot.Endpoint> servers = new LinkedHashMap<>();
        servers.put("GET /orders/{id}", new MetricsSnapshot.Endpoint(100, 2, 5, 4096, 3, 10, 40, 90));

        Map<String, MetricsSnapshot.Endpoint> clients = new LinkedHashMap<>();
        clients.put("inventory:8080", new MetricsSnapshot.Endpoint(20, 4, 3, 1024, 0, 8, 60, 120));

        Map<String, Long> failures = new LinkedHashMap<>();
        failures.put("inventory:8080", 4L);
        failures.put("payments:8080", 7L);

        return MetricsExport.builder()
                .serviceName("orders")
                .instanceId("instance-1")
                .capturedAtMillis(1_767_225_600_000L)
                .snapshot(new MetricsSnapshot(2, servers, clients, failures))
                .build();
    }

    private URI startServer(int statusCode) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/_bulk", exchange -> handle(exchange, statusCode));
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/_bulk");
    }

    private void handle(HttpExchange exchange, int statusCode) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        captured.add(new CapturedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Content-Type"),
                body));
        exchange.sendResponseHeaders(statusCode, -1);
        exchange.close();
    }

    private record CapturedRequest(String method, String path, String contentType, String body) {
    }
}
