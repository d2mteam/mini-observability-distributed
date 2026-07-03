package com.core.metrics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SimpleMetricsRegistry implements MetricsRegistry {
    private final ConcurrentHashMap<String, EndpointStats> byEndpoint = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> consecutiveFailures = new ConcurrentHashMap<>();
    private final AtomicLong inFlight = new AtomicLong();
    private final MetricsConfig config;

    public SimpleMetricsRegistry() {
        this(MetricsConfig.defaults());
    }

    public SimpleMetricsRegistry(MetricsConfig config) {
        this.config = config;
    }

    @Override
    public void onRequestStart() {
        inFlight.incrementAndGet();
    }

    @Override
    public void onRequestEnd(String endpoint, long durationMillis, boolean error, long bytes) {
        inFlight.decrementAndGet();
        stats(endpoint).record(durationMillis, error, bytes, config.slowThresholdMillis());
    }

    @Override
    public void onConnectionOpened(String endpoint) {
        stats(endpoint).connectionOpened();
    }

    @Override
    public void onConnectionClosed(String endpoint) {
        EndpointStats s = byEndpoint.get(endpoint);
        if (s != null) s.connectionClosed();
    }

    @Override
    public void onDestinationResult(String destination, boolean ok) {
        AtomicLong streak = consecutiveFailures.computeIfAbsent(destination, k -> new AtomicLong());
        if (ok) streak.set(0);
        else streak.incrementAndGet();
    }

    @Override
    public MetricsSnapshot snapshot() {
        Map<String, MetricsSnapshot.Endpoint> endpoints = new LinkedHashMap<>();
        byEndpoint.forEach((endpoint, stats) -> endpoints.put(endpoint, stats.snapshot()));
        Map<String, Long> failures = new LinkedHashMap<>();
        consecutiveFailures.forEach((destination, streak) -> failures.put(destination, streak.get()));
        return new MetricsSnapshot(inFlight.get(), endpoints, failures);
    }

    private EndpointStats stats(String endpoint) {
        return byEndpoint.computeIfAbsent(endpoint, key -> new EndpointStats());
    }
}
