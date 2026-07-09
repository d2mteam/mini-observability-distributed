package com.local.receiver.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record MetricsSnapshotRecord(long inFlightRequests,
                                    Map<String, EndpointMetricsRecord> serverEndpoints,
                                    Map<String, EndpointMetricsRecord> clientCalls,
                                    Map<String, Long> consecutiveFailures) {
    public MetricsSnapshotRecord {
        serverEndpoints = copy(serverEndpoints);
        clientCalls = copy(clientCalls);
        consecutiveFailures = copy(consecutiveFailures);
    }

    public static MetricsSnapshotRecord empty() {
        return new MetricsSnapshotRecord(0, Map.of(), Map.of(), Map.of());
    }

    private static <T> Map<String, T> copy(Map<String, T> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(source));
    }
}
