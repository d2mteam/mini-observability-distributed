package com.core.export.metrics;


import com.core.export.ServiceIdentity;
import com.core.metrics.MetricsRegistry;
import lombok.Builder;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MetricsPushExporter implements AutoCloseable {
    private final ServiceIdentity serviceIdentity;
    private final MetricsExportSink metricsExportSink;
    private final MetricsRegistry metricsRegistry;
    private final ScheduledExecutorService scheduler;
    private final long intervalSeconds;
    private ScheduledFuture<?> task;

    @Builder
    public MetricsPushExporter(ServiceIdentity serviceIdentity,
                               MetricsExportSink metricsExportSink,
                               MetricsRegistry metricsRegistry,
                               ScheduledExecutorService scheduler,
                               long intervalSeconds) {
        this.serviceIdentity = serviceIdentity;
        this.metricsExportSink = metricsExportSink;
        this.metricsRegistry = metricsRegistry;
        this.scheduler = scheduler;
        this.intervalSeconds = intervalSeconds;
    }

    public void start() {
        task = scheduler.scheduleAtFixedRate(this::flushQuietly, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    public void flush() throws Exception {
        MetricsExport metricsExport = MetricsExport.builder()
                .serviceName(serviceIdentity.serviceName())
                .instanceId(serviceIdentity.instanceId())
                .capturedAtMillis(System.currentTimeMillis())
                .snapshot(metricsRegistry.snapshot())
                .build();
        metricsExportSink.send(metricsExport);
    }

    private void flushQuietly() {
        try {
            flush();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void close() {
        if (task != null) {
            task.cancel(false);
        }
        flushQuietly();
    }
}
