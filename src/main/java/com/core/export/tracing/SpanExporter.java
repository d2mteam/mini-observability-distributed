package com.core.export.tracing;

import com.core.export.ServiceIdentity;
import com.core.tracing.Span;
import com.core.tracing.handler.SpanHandler;
import lombok.AccessLevel;
import lombok.Builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class SpanExporter implements SpanHandler, AutoCloseable {
    public static final int DEFAULT_QUEUE_CAPACITY = 1024;
    public static final int DEFAULT_BATCH_SIZE = 64;
    public static final long DEFAULT_MAX_DELAY_MILLIS = 1_000;

    private final ServiceIdentity serviceIdentity;
    private final SpanSink spanSink;
    private final ArrayBlockingQueue<SpanRecord> queue;
    private final int batchSize;
    private final long maxDelayMillis;

    // Mini-project tradeoff: approximate under high concurrency. Use LongAdder/AtomicLong later
    // if dropped span accounting becomes part of the public contract.
    private long droppedCount;
    private volatile boolean running;
    private Thread workerThread;

    public SpanExporter(ServiceIdentity serviceIdentity,
                        SpanSink spanSink,
                        int queueCapacity,
                        int batchSize,
                        long maxDelayMillis) {
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (maxDelayMillis <= 0) {
            throw new IllegalArgumentException("maxDelayMillis must be positive");
        }
        this.serviceIdentity = Objects.requireNonNull(serviceIdentity, "serviceIdentity");
        this.spanSink = Objects.requireNonNull(spanSink, "spanSink");
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.batchSize = batchSize;
        this.maxDelayMillis = maxDelayMillis;
    }

    @Builder(access = AccessLevel.PUBLIC)
    private static SpanExporter create(ServiceIdentity serviceIdentity,
                                       SpanSink spanSink,
                                       Integer queueCapacity,
                                       Integer batchSize,
                                       Long maxDelayMillis) {
        return new SpanExporter(
                serviceIdentity,
                spanSink,
                queueCapacity == null ? DEFAULT_QUEUE_CAPACITY : queueCapacity,
                batchSize == null ? DEFAULT_BATCH_SIZE : batchSize,
                maxDelayMillis == null ? DEFAULT_MAX_DELAY_MILLIS : maxDelayMillis
        );
    }

    @Override
    public void handle(Span span) {
        if (span == null) {
            droppedCount++;
            return;
        }

        SpanRecord record = SpanRecord.from(span);
        if (!queue.offer(record)) {
            droppedCount++;
        }
    }

    public void start() {
        if (running) {
            return;
        }

        running = true;
        Thread worker = new Thread(this::runWorker, "span-exporter-" + serviceIdentity.serviceName());
        worker.setDaemon(true);
        workerThread = worker;
        worker.start();
    }

    public void flush() {
        List<SpanRecord> batch = new ArrayList<>(batchSize);
        while (true) {
            batch.clear();
            queue.drainTo(batch, batchSize);
            if (batch.isEmpty()) {
                return;
            }
            exportRecords(List.copyOf(batch));
        }
    }

    @Override
    public void close() {
        running = false;
        Thread worker = workerThread;
        if (worker != null) {
            worker.interrupt();
            try {
                worker.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // No export lock in this demo: after the worker has stopped, shutdown draining
        // is single-threaded. Do not call flush() concurrently with the worker in this version.
        flush();
        closeSink();
    }

    public long droppedCount() {
        return droppedCount;
    }

    private void runWorker() {
        while (running && !Thread.currentThread().isInterrupted()) {
            exportNextBatch();
        }
    }

    private void exportNextBatch() {
        List<SpanRecord> batch = new ArrayList<>(batchSize);
        try {
            SpanRecord first = queue.poll(maxDelayMillis, TimeUnit.MILLISECONDS);
            if (first == null) {
                return;
            }

            // Simple demo batching: wait for the first span, then take what is already queued.
            // Production exporters usually keep a deadline and wait for more spans before sending.
            batch.add(first);
            queue.drainTo(batch, batchSize - 1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        if (!batch.isEmpty()) {
            exportRecords(batch);
        }
    }

    private void exportRecords(List<SpanRecord> records) {
        if (records.isEmpty()) {
            return;
        }

        try {
            SpanExport export = new SpanExport(
                    serviceIdentity.serviceName(),
                    serviceIdentity.instanceId(),
                    System.currentTimeMillis(),
                    records);
            spanSink.send(export);
        } catch (Exception ignored) {
            // Demo exporter: export failures must not break the observed application.
            // TODO: add retry/requeue/failure metrics if this grows beyond a mini project.
        }
    }

    private void closeSink() {
        if (spanSink instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // Export cleanup must not fail application shutdown.
            }
        }
    }
}
