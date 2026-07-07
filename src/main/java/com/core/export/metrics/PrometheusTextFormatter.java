package com.core.export.metrics;

import com.core.metrics.MetricsSnapshot;

import java.util.Map;
import java.util.Objects;
import java.util.function.ToLongFunction;

public class PrometheusTextFormatter {
    public String format(MetricsExport export) {
        Objects.requireNonNull(export, "export");

        MetricsSnapshot snapshot = export.snapshot() == null ? MetricsSnapshot.empty() : export.snapshot();
        StringBuilder out = new StringBuilder();

        appendSingleGauge(out,
                "mini_in_flight_requests",
                "Current in-flight server requests and client calls.",
                export,
                snapshot.inFlightRequests());

        appendServerMetrics(out, export, snapshot.serverEndpoints());
        appendClientMetrics(out, export, snapshot.clientCalls());
        appendFailureMetrics(out, export, snapshot.consecutiveFailures());

        return out.toString();
    }

    private void appendServerMetrics(StringBuilder out,
                                     MetricsExport export,
                                     Map<String, MetricsSnapshot.Endpoint> endpoints) {
        if (endpoints.isEmpty()) {
            return;
        }
        appendServerCounter(out, export, endpoints, "mini_server_requests_total", "Total server requests.", MetricsSnapshot.Endpoint::count);
        appendServerCounter(out, export, endpoints, "mini_server_request_errors_total", "Total failed server requests.", MetricsSnapshot.Endpoint::errors);
        appendServerCounter(out, export, endpoints, "mini_server_slow_requests_total", "Total slow server requests.", MetricsSnapshot.Endpoint::slow);
        appendServerCounter(out, export, endpoints, "mini_server_request_bytes_total", "Total server request bytes.", MetricsSnapshot.Endpoint::totalBytes);
        appendServerGauge(out, export, endpoints, "mini_server_active_connections", "Current active server-side stateful connections.", MetricsSnapshot.Endpoint::activeConnections);
        appendServerLatency(out, export, endpoints);
    }

    private void appendClientMetrics(StringBuilder out,
                                     MetricsExport export,
                                     Map<String, MetricsSnapshot.Endpoint> calls) {
        if (calls.isEmpty()) {
            return;
        }
        appendClientCounter(out, export, calls, "mini_client_calls_total", "Total client calls.", MetricsSnapshot.Endpoint::count);
        appendClientCounter(out, export, calls, "mini_client_call_errors_total", "Total failed client calls.", MetricsSnapshot.Endpoint::errors);
        appendClientCounter(out, export, calls, "mini_client_slow_calls_total", "Total slow client calls.", MetricsSnapshot.Endpoint::slow);
        appendClientCounter(out, export, calls, "mini_client_request_bytes_total", "Total client request bytes.", MetricsSnapshot.Endpoint::totalBytes);
        appendClientLatency(out, export, calls);
    }

    private void appendFailureMetrics(StringBuilder out, MetricsExport export, Map<String, Long> failures) {
        if (failures.isEmpty()) {
            return;
        }
        declare(out, "mini_client_consecutive_failures", "Current consecutive failure count per client destination.", "gauge");
        failures.forEach((destination, value) -> appendSample(out,
                "mini_client_consecutive_failures",
                export,
                "destination",
                destination,
                value == null ? 0 : value));
    }

    private void appendServerLatency(StringBuilder out,
                                     MetricsExport export,
                                     Map<String, MetricsSnapshot.Endpoint> endpoints) {
        // The core snapshot exposes percentiles only. Without bucket counts and sum,
        // this cannot be exported as a Prometheus histogram without inventing data.
        appendServerGauge(out, export, endpoints, "mini_server_request_latency_p50_millis", "P50 server request latency in milliseconds.", MetricsSnapshot.Endpoint::p50Millis);
        appendServerGauge(out, export, endpoints, "mini_server_request_latency_p95_millis", "P95 server request latency in milliseconds.", MetricsSnapshot.Endpoint::p95Millis);
        appendServerGauge(out, export, endpoints, "mini_server_request_latency_p99_millis", "P99 server request latency in milliseconds.", MetricsSnapshot.Endpoint::p99Millis);
    }

    private void appendClientLatency(StringBuilder out,
                                     MetricsExport export,
                                     Map<String, MetricsSnapshot.Endpoint> calls) {
        // Same note as server latency: these are percentile gauges, not histograms.
        appendClientGauge(out, export, calls, "mini_client_call_latency_p50_millis", "P50 client call latency in milliseconds.", MetricsSnapshot.Endpoint::p50Millis);
        appendClientGauge(out, export, calls, "mini_client_call_latency_p95_millis", "P95 client call latency in milliseconds.", MetricsSnapshot.Endpoint::p95Millis);
        appendClientGauge(out, export, calls, "mini_client_call_latency_p99_millis", "P99 client call latency in milliseconds.", MetricsSnapshot.Endpoint::p99Millis);
    }
    // ToLongFunction
    private void appendServerCounter(StringBuilder out,
                                     MetricsExport export,
                                     Map<String, MetricsSnapshot.Endpoint> endpoints,
                                     String name,
                                     String help,
                                     ToLongFunction<MetricsSnapshot.Endpoint> value) {
        appendServerMetric(out, export, endpoints, name, help, "counter", value);
    }

    private void appendServerGauge(StringBuilder out,
                                   MetricsExport export,
                                   Map<String, MetricsSnapshot.Endpoint> endpoints,
                                   String name,
                                   String help,
                                   ToLongFunction<MetricsSnapshot.Endpoint> value) {
        appendServerMetric(out, export, endpoints, name, help, "gauge", value);
    }

    private void appendClientCounter(StringBuilder out,
                                     MetricsExport export,
                                     Map<String, MetricsSnapshot.Endpoint> calls,
                                     String name,
                                     String help,
                                     ToLongFunction<MetricsSnapshot.Endpoint> value) {
        appendClientMetric(out, export, calls, name, help, "counter", value);
    }

    private void appendClientGauge(StringBuilder out,
                                   MetricsExport export,
                                   Map<String, MetricsSnapshot.Endpoint> calls,
                                   String name,
                                   String help,
                                   ToLongFunction<MetricsSnapshot.Endpoint> value) {
        appendClientMetric(out, export, calls, name, help, "gauge", value);
    }

    private void appendServerMetric(StringBuilder out,
                                    MetricsExport export,
                                    Map<String, MetricsSnapshot.Endpoint> endpoints,
                                    String name,
                                    String help,
                                    String type,
                                    ToLongFunction<MetricsSnapshot.Endpoint> value) {
        declare(out, name, help, type);
        endpoints.forEach((route, endpoint) -> {
            if (endpoint != null) {
                appendSample(out, name, export, "route", route, value.applyAsLong(endpoint));
            }
        });
    }

    private void appendClientMetric(StringBuilder out,
                                    MetricsExport export,
                                    Map<String, MetricsSnapshot.Endpoint> calls,
                                    String name,
                                    String help,
                                    String type,
                                    ToLongFunction<MetricsSnapshot.Endpoint> value) {
        declare(out, name, help, type);
        calls.forEach((destination, endpoint) -> {
            if (endpoint != null) {
                appendSample(out, name, export, "destination", destination, value.applyAsLong(endpoint));
            }
        });
    }

    private void appendSingleGauge(StringBuilder out,
                                   String name,
                                   String help,
                                   MetricsExport export,
                                   long value) {
        declare(out, name, help, "gauge");
        appendSample(out, name, export, null, null, value);
    }

    private void declare(StringBuilder out, String name, String help, String type) {
        out.append("# HELP ").append(name).append(' ').append(help).append('\n');
        out.append("# TYPE ").append(name).append(' ').append(type).append('\n');
    }

    private void appendSample(StringBuilder out,
                              String name,
                              MetricsExport export,
                              String extraKey,
                              String extraValue,
                              long value) {
        out.append(name);
        appendLabels(out, export, extraKey, extraValue);
        out.append(' ').append(value).append('\n');
    }

    private void appendLabels(StringBuilder out, MetricsExport export, String extraKey, String extraValue) {
        boolean hasService = hasText(export.serviceName());
        boolean hasInstance = hasText(export.instanceId());
        boolean hasExtra = hasText(extraKey) && hasText(extraValue);
        if (!hasService && !hasInstance && !hasExtra) {
            return;
        }

        out.append('{');
        boolean first = appendLabel(out, true, "service", export.serviceName());
        first = appendLabel(out, first, "instance", export.instanceId());
        appendLabel(out, first, extraKey, extraValue);
        out.append('}');
    }

    private boolean appendLabel(StringBuilder out, boolean first, String key, String value) {
        if (!hasText(key) || !hasText(value)) {
            return first;
        }
        if (!first) {
            out.append(',');
        }
        out.append(key).append("=\"").append(escapeLabelValue(value)).append('"');
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String escapeLabelValue(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\"", "\\\"");
    }
}
