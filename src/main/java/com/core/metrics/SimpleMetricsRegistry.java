package com.core.metrics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SimpleMetricsRegistry implements MetricsRegistry {
    private final ConcurrentHashMap<String, EndpointStats> serverEndpoints = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, EndpointStats> clientCalls = new ConcurrentHashMap<>();
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
    public void onServerRequestStart() {
        inFlight.incrementAndGet();
    }

    @Override
    public void onServerRequestEnd(String route, long durationMillis, boolean error, long bytes) {
        inFlight.decrementAndGet();
        serverStats(route).record(durationMillis, error, bytes, config.slowThresholdMillis());
    }

    @Override
    public void onClientCallStart() {
        inFlight.incrementAndGet();
    }

    @Override
    public void onClientCallEnd(String destination, long durationMillis, boolean error, long bytes) {
        inFlight.decrementAndGet();
        clientStats(destination).record(durationMillis, error, bytes, config.slowThresholdMillis());
    }

    @Override
    public void onConnectionOpened(String endpoint) {
        serverStats(endpoint).connectionOpened();
    }

    @Override
    public void onConnectionClosed(String endpoint) {
        EndpointStats s = serverEndpoints.get(endpoint);
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
        Map<String, MetricsSnapshot.Endpoint> servers = new LinkedHashMap<>();
        serverEndpoints.forEach((endpoint, stats) -> servers.put(endpoint, stats.snapshot()));
        Map<String, MetricsSnapshot.Endpoint> clients = new LinkedHashMap<>();
        clientCalls.forEach((destination, stats) -> clients.put(destination, stats.snapshot()));
        Map<String, Long> failures = new LinkedHashMap<>();
        consecutiveFailures.forEach((destination, streak) -> failures.put(destination, streak.get()));
        return new MetricsSnapshot(inFlight.get(), servers, clients, failures);
    }

    private EndpointStats serverStats(String endpoint) {
        return serverEndpoints.computeIfAbsent(endpoint, key -> new EndpointStats());
    }

    private EndpointStats clientStats(String destination) {
        return clientCalls.computeIfAbsent(destination, key -> new EndpointStats());
    }
}
