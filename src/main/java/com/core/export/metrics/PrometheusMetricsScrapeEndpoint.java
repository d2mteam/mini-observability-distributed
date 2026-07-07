package com.core.export.metrics;

import com.core.export.ServiceIdentity;
import com.core.metrics.MetricsRegistry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class PrometheusMetricsScrapeEndpoint implements MetricsScrapeEndpoint {
    public static final String CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8";

    private final ServiceIdentity serviceIdentity;
    private final MetricsRegistry metricsRegistry;
    private final String host;
    private final int port;
    private final String path;
    private final PrometheusTextFormatter formatter = new PrometheusTextFormatter();

    private HttpServer server;

    public PrometheusMetricsScrapeEndpoint(ServiceIdentity serviceIdentity,
                                           MetricsRegistry metricsRegistry,
                                           String host,
                                           int port,
                                           String path) {
        this.serviceIdentity = Objects.requireNonNull(serviceIdentity, "serviceIdentity");
        this.metricsRegistry = Objects.requireNonNull(metricsRegistry, "metricsRegistry");
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.path = normalizePath(path);
    }

    @Override
    public void start() throws Exception {
        // Demo endpoint: no custom executor/synchronization. Add explicit lifecycle
        // guards and executor ownership later if this becomes production-facing.
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext(path, this::handle);
        server.start();
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            MetricsExport export = MetricsExport.builder()
                    .serviceName(serviceIdentity.serviceName())
                    .instanceId(serviceIdentity.instanceId())
                    .capturedAtMillis(System.currentTimeMillis())
                    .snapshot(metricsRegistry.snapshot())
                    .build();
            byte[] body = formatter.format(export).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/metrics";
        }
        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }
}
