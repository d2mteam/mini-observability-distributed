package com.core.export.tracing;

import com.core.tracing.Span;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ZipkinSpanSinkTest {
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
    void postsZipkinV2SpansToEndpoint() throws Exception {
        URI endpoint = startServer(202);
        ZipkinSpanSink sink = new ZipkinSpanSink(endpoint);

        sink.send(spanExport());

        assertEquals(1, captured.size());
        CapturedRequest request = captured.getFirst();
        assertEquals("POST", request.method());
        assertEquals("/api/v2/spans", request.path());
        assertEquals("application/json", request.contentType());

        JsonNode spans = MAPPER.readTree(request.body());
        assertEquals(2, spans.size());

        JsonNode server = spans.get(0);
        assertEquals("0af7651916cd43dd8448eb211c80319c", server.get("traceId").asText());
        assertEquals("1111111111111111", server.get("id").asText());
        assertFalse(server.has("parentId"));
        assertEquals("SERVER", server.get("kind").asText());
        assertEquals("GET /orders/{id}", server.get("name").asText());
        assertEquals(1_000_000, server.get("timestamp").asLong());
        assertEquals(15_000, server.get("duration").asLong());
        assertEquals("orders", server.get("localEndpoint").get("serviceName").asText());
        assertEquals("200", server.get("tags").get("http.status_code").asText());
        assertEquals("OK", server.get("tags").get("mini.status").asText());
        assertEquals("true", server.get("tags").get("mini.sampled").asText());
        assertEquals("instance-1", server.get("tags").get("mini.instance_id").asText());

        JsonNode client = spans.get(1);
        assertEquals("2222222222222222", client.get("id").asText());
        assertEquals("1111111111111111", client.get("parentId").asText());
        assertEquals("CLIENT", client.get("kind").asText());
        assertFalse(client.has("duration"));
        assertEquals("ERROR", client.get("tags").get("mini.status").asText());
        assertEquals("true", client.get("tags").get("error").asText());
    }

    @Test
    void throwsWhenServerReturnsNon2xx() throws Exception {
        URI endpoint = startServer(503);
        ZipkinSpanSink sink = new ZipkinSpanSink(endpoint);

        assertThrows(IllegalStateException.class, () -> sink.send(spanExport()));
    }

    private static SpanExport spanExport() {
        return new SpanExport(
                "orders",
                "instance-1",
                2_000,
                List.of(
                        SpanRecord.builder()
                                .traceId("0af7651916cd43dd8448eb211c80319c")
                                .spanId("1111111111111111")
                                .name("GET /orders/{id}")
                                .kind(Span.Kind.SERVER)
                                .startEpochMillis(1_000)
                                .startNanos(10_000_000)
                                .durationMillis(15)
                                .status(Span.Status.OK)
                                .sampled(true)
                                .attributes(Map.of("http.status_code", "200"))
                                .build(),
                        SpanRecord.builder()
                                .traceId("0af7651916cd43dd8448eb211c80319c")
                                .spanId("2222222222222222")
                                .parentSpanId("1111111111111111")
                                .name("HTTP")
                                .kind(Span.Kind.CLIENT)
                                .startEpochMillis(1_002)
                                .startNanos(25_000_000)
                                .durationMillis(0)
                                .status(Span.Status.ERROR)
                                .sampled(true)
                                .attributes(Map.of("server.address", "inventory"))
                                .build()));
    }

    private URI startServer(int statusCode) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v2/spans", exchange -> handle(exchange, statusCode));
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v2/spans");
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
