package com.core.export.tracing;

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

class HttpSpanSinkTest {
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
        URI endpoint = startServer(202);
        HttpSpanSink sink = new HttpSpanSink(endpoint);

        sink.send(spanExport());

        assertEquals(1, captured.size());
        CapturedRequest request = captured.getFirst();
        assertEquals("POST", request.method());
        assertEquals("/spans", request.path());
        assertEquals("application/json", request.contentType());
        JsonNode json = MAPPER.readTree(request.body());
        assertEquals("orders", json.get("serviceName").asText());
        assertEquals("instance-1", json.get("instanceId").asText());
        assertEquals(0, json.get("spans").size());
    }

    @Test
    void throwsWhenServerReturnsNon2xx() throws Exception {
        URI endpoint = startServer(503);
        HttpSpanSink sink = new HttpSpanSink(endpoint);

        assertThrows(IllegalStateException.class, () -> sink.send(spanExport()));
    }

    private static SpanExport spanExport() {
        return new SpanExport("orders", "instance-1", 123, List.of());
    }

    private URI startServer(int statusCode) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/spans", exchange -> handle(exchange, statusCode));
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/spans");
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
