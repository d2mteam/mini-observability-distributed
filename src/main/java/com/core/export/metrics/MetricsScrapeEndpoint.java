package com.core.export.metrics;

/**
 * Pull-style metrics endpoint, for backends such as Prometheus that scrape the app.
 *
 * <p>This is intentionally separate from {@link MetricsExportSink}: sinks push data out,
 * scrape endpoints expose data for another process to pull.</p>
 */
public interface MetricsScrapeEndpoint extends AutoCloseable {
    void start() throws Exception;

    @Override
    void close() throws Exception;
}
