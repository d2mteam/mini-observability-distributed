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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpMetricsExportSinkTest {
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
    void postsJsonToEndpoint() throws Exception {
        URI endpoint = startServer(204);
        HttpMetricsExportSink sink = new HttpMetricsExportSink(endpoint);

        sink.send(metricsExport());

        assertEquals(1, captured.size());
        CapturedRequest request = captured.getFirst();
        assertEquals("POST", request.method());
        assertEquals("/metrics", request.path());
        assertEquals("application/json", request.contentType());
        JsonNode json = MAPPER.readTree(request.body());
        assertEquals("orders", json.get("serviceName").asText());
        assertEquals("instance-1", json.get("instanceId").asText());
        assertEquals(123, json.get("capturedAtMillis").asLong());
        assertEquals(0, json.get("snapshot").get("inFlightRequests").asLong());
    }

    @Test
    void throwsWhenServerReturnsNon2xx() throws Exception {
        URI endpoint = startServer(500);
        HttpMetricsExportSink sink = new HttpMetricsExportSink(endpoint);

        assertThrows(IllegalStateException.class, () -> sink.send(metricsExport()));
    }

    private static MetricsExport metricsExport() {
        return MetricsExport.builder()
                .serviceName("orders")
                .instanceId("instance-1")
                .capturedAtMillis(123)
                .snapshot(MetricsSnapshot.empty())
                .build();
    }

    private URI startServer(int statusCode) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/metrics", exchange -> handle(exchange, statusCode));
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/metrics");
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
