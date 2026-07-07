package com.core.export.metrics;

import com.core.metrics.MetricsSnapshot;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrometheusTextFormatterTest {
    private final PrometheusTextFormatter formatter = new PrometheusTextFormatter();

    @Test
    void formatsEmptySnapshotWithInFlightGauge() {
        String text = formatter.format(MetricsExport.builder()
                .serviceName("orders")
                .instanceId("instance-1")
                .capturedAtMillis(123)
                .snapshot(MetricsSnapshot.empty())
                .build());

        assertTrue(text.contains("# TYPE mini_in_flight_requests gauge\n"));
        assertTrue(text.contains("mini_in_flight_requests{service=\"orders\",instance=\"instance-1\"} 0\n"));
        assertTrue(text.endsWith("\n"));
    }

    @Test
    void formatsServerEndpointsAndClientCallsSeparately() {
        Map<String, MetricsSnapshot.Endpoint> servers = new LinkedHashMap<>();
        servers.put("GET /orders/{id}", new MetricsSnapshot.Endpoint(2, 1, 1, 300, 0, 5, 50, 75));
        Map<String, MetricsSnapshot.Endpoint> clients = new LinkedHashMap<>();
        clients.put("inventory:8080", new MetricsSnapshot.Endpoint(3, 0, 1, 120, 0, 7, 20, 40));
        Map<String, Long> failures = new LinkedHashMap<>();
        failures.put("inventory:8080", 2L);

        String text = formatter.format(MetricsExport.builder()
                .serviceName("orders")
                .instanceId("instance-1")
                .capturedAtMillis(123)
                .snapshot(new MetricsSnapshot(0, servers, clients, failures))
                .build());

        assertTrue(text.contains("mini_server_requests_total{service=\"orders\",instance=\"instance-1\",route=\"GET /orders/{id}\"} 2\n"));
        assertTrue(text.contains("mini_server_request_errors_total{service=\"orders\",instance=\"instance-1\",route=\"GET /orders/{id}\"} 1\n"));
        assertTrue(text.contains("mini_server_request_latency_p95_millis{service=\"orders\",instance=\"instance-1\",route=\"GET /orders/{id}\"} 50\n"));
        assertTrue(text.contains("mini_client_calls_total{service=\"orders\",instance=\"instance-1\",destination=\"inventory:8080\"} 3\n"));
        assertTrue(text.contains("mini_client_call_latency_p99_millis{service=\"orders\",instance=\"instance-1\",destination=\"inventory:8080\"} 40\n"));
        assertTrue(text.contains("mini_client_consecutive_failures{service=\"orders\",instance=\"instance-1\",destination=\"inventory:8080\"} 2\n"));
    }

    @Test
    void escapesLabelValues() {
        Map<String, MetricsSnapshot.Endpoint> servers = new LinkedHashMap<>();
        servers.put("GET /quote\"x\nback\\slash", new MetricsSnapshot.Endpoint(1, 0, 0, 0, 0, 1, 1, 1));

        String text = formatter.format(MetricsExport.builder()
                .serviceName("svc")
                .instanceId("i1")
                .capturedAtMillis(123)
                .snapshot(new MetricsSnapshot(0, servers, Map.of(), Map.of()))
                .build());

        assertTrue(text.contains("route=\"GET /quote\\\"x\\nback\\\\slash\""));
    }

    @Test
    void doesNotPretendPercentilesArePrometheusHistograms() {
        Map<String, MetricsSnapshot.Endpoint> servers = new LinkedHashMap<>();
        servers.put("GET /x", new MetricsSnapshot.Endpoint(1, 0, 0, 0, 0, 1, 2, 3));

        String text = formatter.format(MetricsExport.builder()
                .serviceName("svc")
                .instanceId("i1")
                .capturedAtMillis(123)
                .snapshot(new MetricsSnapshot(0, servers, Map.of(), Map.of()))
                .build());

        assertTrue(text.contains("mini_server_request_latency_p50_millis"));
        assertFalse(text.contains("_bucket"));
        assertFalse(text.contains("_sum"));
        assertFalse(text.contains("_count"));
    }
}
