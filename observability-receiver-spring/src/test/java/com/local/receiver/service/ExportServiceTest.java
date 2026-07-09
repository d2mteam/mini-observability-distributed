package com.local.receiver.service;

import com.local.receiver.api.ApiModels.AiContextBlock;
import com.local.receiver.model.EndpointMetricsRecord;
import com.local.receiver.model.MetricsExportRecord;
import com.local.receiver.model.MetricsSnapshotRecord;
import com.local.receiver.model.SpanExportRecord;
import com.local.receiver.model.SpanRecord;
import com.local.receiver.store.MetricsRecordStore;
import com.local.receiver.store.TraceRecordStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExportServiceTest {

    @Test
    void aiContextFocusesEndpointAndKeepsRelatedErrorEvidence() {
        MetricsRecordStore metricsStore = new MetricsRecordStore(20);
        TraceRecordStore traceStore = new TraceRecordStore(20);
        ExportService service = new ExportService(
                new MetricsQueryService(metricsStore),
                new TraceQueryService(traceStore, 200)
        );

        metricsStore.append(metricsExport("message-service", "message-service-1", 2000,
                Map.of("/messages", endpoint(80, 4, 9, 18, 240, 510)),
                Map.of("postgresql", endpoint(160, 4, 12, 8, 180, 420)),
                Map.of("postgresql", 2L)));
        metricsStore.append(metricsExport("chat-gateway", "chat-gateway-1", 1900,
                Map.of("/app/chat.send", endpoint(100, 2, 6, 22, 260, 530)),
                Map.of(
                        "localhost:8082", endpoint(100, 4, 10, 20, 250, 520),
                        "localhost:7003", endpoint(90, 0, 3, 8, 40, 80)
                ),
                Map.of("localhost:8082", 1L)));

        traceStore.append(spanExport("chat-gateway", List.of(
                span("trace-1", "root", null, "ws send /app/chat.send", "SERVER", 1000, 500, "ERROR",
                        Map.of("protocol", "websocket", "messaging.destination", "/app/chat.send")),
                span("trace-1", "http-client", "root", "post http://localhost:8082/messages", "CLIENT", 1010, 250, "ERROR",
                        Map.of("protocol", "http", "server.address", "localhost:8082", "http.status_code", "500")),
                span("trace-1", "presence-client", "root", "rsocket presence.stream", "CLIENT", 1015, 40, "OK",
                        Map.of("protocol", "rsocket", "server.address", "localhost:7003", "rsocket.route", "presence.stream"))
        )));
        traceStore.append(spanExport("message-service", List.of(
                span("trace-1", "http-server", "http-client", "post /messages", "SERVER", 1020, 220, "ERROR",
                        Map.of("protocol", "http", "http.status_code", "500")),
                span("trace-1", "jdbc-1", "http-server", "jdbc insert", "CLIENT", 1030, 180, "ERROR",
                        Map.of("protocol", "jdbc", "db.system", "postgresql", "db.operation", "INSERT", "error", "SQLTimeoutException"))
        )));

        AiContextBlock block = service.aiContext("/app/chat.send", 2);

        assertThat(block.endpoint()).isEqualTo("/app/chat.send");
        assertThat(block.primaryMetrics()).isNotNull();
        assertThat(block.primaryMetrics().snapshot().serverEndpoints()).containsKey("/app/chat.send");
        assertThat(block.traceIds()).containsExactly("trace-1");
        assertThat(block.errorPatterns()).anySatisfy(pattern -> {
            assertThat(pattern.protocol()).isEqualTo("jdbc");
            assertThat(pattern.error()).isEqualTo("SQLTimeoutException");
            assertThat(pattern.traceIds()).containsExactly("trace-1");
        });
        assertThat(block.relatedMetrics())
                .anySatisfy(metric -> {
                    assertThat(metric.side()).isEqualTo("clientCalls");
                    assertThat(metric.key()).isEqualTo("localhost:8082");
                })
                .anySatisfy(metric -> {
                    assertThat(metric.side()).isEqualTo("serverEndpoints");
                    assertThat(metric.key()).isEqualTo("/messages");
                })
                .anySatisfy(metric -> {
                    assertThat(metric.side()).isEqualTo("clientCalls");
                    assertThat(metric.key()).isEqualTo("postgresql");
                    assertThat(metric.consecutiveFailures()).isEqualTo(2L);
                });
        assertThat(block.relatedMetrics())
                .noneSatisfy(metric -> assertThat(metric.key()).isEqualTo("localhost:7003"));
        assertThat(block.traces()).containsKey("trace-1");
    }

    private static MetricsExportRecord metricsExport(String serviceName,
                                                     String instanceId,
                                                     long capturedAtMillis,
                                                     Map<String, EndpointMetricsRecord> serverEndpoints,
                                                     Map<String, EndpointMetricsRecord> clientCalls,
                                                     Map<String, Long> consecutiveFailures) {
        return new MetricsExportRecord(
                serviceName,
                instanceId,
                capturedAtMillis,
                new MetricsSnapshotRecord(0, serverEndpoints, clientCalls, consecutiveFailures)
        );
    }

    private static EndpointMetricsRecord endpoint(long count,
                                                  long errors,
                                                  long slow,
                                                  long p50Millis,
                                                  long p95Millis,
                                                  long p99Millis) {
        return new EndpointMetricsRecord(count, errors, slow, 0, 0, p50Millis, p95Millis, p99Millis);
    }

    private static SpanExportRecord spanExport(String serviceName, List<SpanRecord> spans) {
        return new SpanExportRecord(serviceName, serviceName + "-1", 1500, spans);
    }

    private static SpanRecord span(String traceId,
                                   String spanId,
                                   String parentSpanId,
                                   String name,
                                   String kind,
                                   long startEpochMillis,
                                   long durationMillis,
                                   String status,
                                   Map<String, String> attributes) {
        return new SpanRecord(traceId, spanId, parentSpanId, name, kind,
                startEpochMillis, 10, durationMillis, status, true, attributes);
    }
}
