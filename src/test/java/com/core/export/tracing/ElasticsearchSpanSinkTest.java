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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticsearchSpanSinkTest {
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
    void postsSpanDocumentsAsBulkNdjson() throws Exception {
        URI endpoint = startServer(200);
        ElasticsearchSpanSink sink = new ElasticsearchSpanSink(endpoint, "mini-spans");

        sink.send(spanExport());

        assertEquals(1, captured.size());
        CapturedRequest request = captured.getFirst();
        assertEquals("POST", request.method());
        assertEquals("/_bulk", request.path());
        assertEquals("application/x-ndjson", request.contentType());
        assertTrue(request.body().endsWith("\n"));

        String[] lines = request.body().split("\n");
        assertEquals(4, lines.length);

        JsonNode firstAction = MAPPER.readTree(lines[0]);
        assertEquals("mini-spans", firstAction.get("index").get("_index").asText());
        assertEquals("0af7651916cd43dd8448eb211c80319c-1111111111111111",
                firstAction.get("index").get("_id").asText());

        JsonNode firstSpan = MAPPER.readTree(lines[1]);
        assertEquals("2026-01-01T00:00:00Z", firstSpan.get("@timestamp").asText());
        assertEquals("orders", firstSpan.get("service").get("name").asText());
        assertEquals("instance-1", firstSpan.get("service").get("node").get("name").asText());
        assertEquals("0af7651916cd43dd8448eb211c80319c", firstSpan.get("trace").get("id").asText());
        assertEquals("1111111111111111", firstSpan.get("span").get("id").asText());
        assertEquals("GET /orders/{id}", firstSpan.get("span").get("name").asText());
        assertEquals("OK", firstSpan.get("mini_span").get("status").asText());
        assertEquals("200", firstSpan.get("mini_span").get("attributes").get("http.status_code").asText());

        JsonNode secondSpan = MAPPER.readTree(lines[3]);
        assertEquals("2222222222222222", secondSpan.get("span").get("id").asText());
        assertEquals("1111111111111111", secondSpan.get("parent").get("id").asText());
        assertEquals("error", secondSpan.get("event").get("type").get(0).asText());
    }

    @Test
    void throwsWhenServerReturnsNon2xx() throws Exception {
        URI endpoint = startServer(503);
        ElasticsearchSpanSink sink = new ElasticsearchSpanSink(endpoint, "mini-spans");

        assertThrows(IllegalStateException.class, () -> sink.send(spanExport()));
    }

    private static SpanExport spanExport() {
        return new SpanExport(
                "orders",
                "instance-1",
                1_767_225_600_999L,
                List.of(
                        SpanRecord.builder()
                                .traceId("0af7651916cd43dd8448eb211c80319c")
                                .spanId("1111111111111111")
                                .name("GET /orders/{id}")
                                .kind(Span.Kind.SERVER)
                                .startEpochMillis(1_767_225_600_000L)
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
                                .startEpochMillis(1_767_225_600_005L)
                                .startNanos(25_000_000)
                                .durationMillis(7)
                                .status(Span.Status.ERROR)
                                .sampled(true)
                                .attributes(Map.of("server.address", "inventory"))
                                .build()));
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
