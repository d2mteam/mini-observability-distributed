package com.core.metrics;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleMetricsRegistryTest {

    @Test
    void recordsCountErrorSlowBytesLatency() {
        var m = new SimpleMetricsRegistry(new MetricsConfig(10));   // slow > 10ms
        m.onServerRequestStart();
        m.onServerRequestEnd("GET /x", 5, false, 100);    // ok, nhanh
        m.onServerRequestStart();
        m.onServerRequestEnd("GET /x", 50, true, 200);    // lỗi, slow

        MetricsSnapshot.Endpoint ep = m.snapshot().serverEndpoints().get("GET /x");
        assertEquals(2, ep.count());
        assertEquals(1, ep.errors());
        assertEquals(1, ep.slow());
        assertEquals(300, ep.totalBytes());
        assertEquals(0, m.snapshot().inFlightRequests());   // 2 start / 2 end
        assertTrue(ep.p99Millis() >= 5, "p99=" + ep.p99Millis());
        assertEquals(50.0, ep.errorRatePercent());          // 1/2
    }

    @Test
    void inFlightReflectsStartMinusEnd() {
        var m = new SimpleMetricsRegistry();
        m.onServerRequestStart();
        m.onClientCallStart();
        assertEquals(2, m.snapshot().inFlightRequests());
        m.onServerRequestEnd("GET /x", 1, false, 0);
        assertEquals(1, m.snapshot().inFlightRequests());
    }

    @Test
    void recordsClientCallsSeparatelyFromServerEndpoints() {
        var m = new SimpleMetricsRegistry(new MetricsConfig(10));
        m.onServerRequestStart();
        m.onServerRequestEnd("GET /orders/{id}", 8, false, 100);
        m.onClientCallStart();
        m.onClientCallEnd("inventory:8080", 25, true, 20);

        MetricsSnapshot snap = m.snapshot();
        assertTrue(snap.serverEndpoints().containsKey("GET /orders/{id}"));
        assertTrue(snap.clientCalls().containsKey("inventory:8080"));
        assertEquals(1, snap.clientCalls().get("inventory:8080").errors());
        assertEquals(0, snap.serverEndpoints().get("GET /orders/{id}").errors());
    }

    @Test
    void activeConnectionsPerEndpoint() {
        var m = new SimpleMetricsRegistry();
        m.onConnectionOpened("/ws/chat");
        m.onConnectionOpened("/ws/chat");
        m.onConnectionOpened("/ws/noti");
        m.onConnectionClosed("/ws/chat");

        MetricsSnapshot snap = m.snapshot();
        assertEquals(1, snap.serverEndpoints().get("/ws/chat").activeConnections());
        assertEquals(1, snap.serverEndpoints().get("/ws/noti").activeConnections());
    }

    @Test
    void consecutiveFailuresResetOnSuccess() {
        var m = new SimpleMetricsRegistry();
        m.onDestinationResult("svc-b", false);
        m.onDestinationResult("svc-b", false);
        m.onDestinationResult("svc-b", false);
        assertEquals(3, m.snapshot().consecutiveFailures().get("svc-b"));
        m.onDestinationResult("svc-b", true);
        assertEquals(0, m.snapshot().consecutiveFailures().get("svc-b"));
    }

    @Test
    void concurrentRecordingIsConsistent() throws Exception {
        var m = new SimpleMetricsRegistry();
        int threads = 8, perThread = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            tasks.add(pool.submit(() -> {
                for (int i = 0; i < perThread; i++) {
                    m.onServerRequestStart();
                    m.onServerRequestEnd("GET /c", 1, false, 10);
                }
            }));
        }
        for (Future<?> f : tasks) f.get();
        pool.shutdown();

        MetricsSnapshot.Endpoint ep = m.snapshot().serverEndpoints().get("GET /c");
        assertEquals((long) threads * perThread, ep.count());
        assertEquals((long) threads * perThread * 10, ep.totalBytes());
        assertEquals(0, m.snapshot().inFlightRequests());   // start/end cân bằng, không leak
    }

    @Test
    void noopDefaultInterfaceRecordsNothing() {
        MetricsRegistry noop = new MetricsRegistry() {};
        noop.onServerRequestStart();
        noop.onServerRequestEnd("x", 1, true, 1);
        noop.onClientCallStart();
        noop.onClientCallEnd("svc", 1, true, 1);
        assertEquals(0, noop.snapshot().inFlightRequests());
        assertTrue(noop.snapshot().serverEndpoints().isEmpty());
        assertTrue(noop.snapshot().clientCalls().isEmpty());
    }
}
