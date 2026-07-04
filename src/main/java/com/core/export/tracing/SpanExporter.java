package com.core.export.tracing;

import com.core.tracing.Span;
import com.core.tracing.handler.SpanHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class SpanExporter implements SpanHandler, AutoCloseable {
    public static final int DEFAULT_QUEUE_CAPACITY = 1024;
    public static final int DEFAULT_BATCH_SIZE = 64;
    public static final long DEFAULT_MAX_DELAY_MILLIS = 1_000;

    private final String serviceName;
    private final String instanceId;
    private final SpanSink spanSink;
    private final ArrayBlockingQueue<SpanRecord> queue;
    private final int batchSize;
    private final long maxDelayMillis;
    private final ObjectWriter writer;
    private final Object exportLock = new Object();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong acceptedCount = new AtomicLong();
    private final AtomicLong exportedCount = new AtomicLong();
    private final AtomicLong droppedCount = new AtomicLong();
    private final AtomicLong failedCount = new AtomicLong();

    private volatile Thread workerThread;

    public SpanExporter(String serviceName, String instanceId, SpanSink spanSink) {
        this(serviceName, instanceId, spanSink,
                DEFAULT_QUEUE_CAPACITY, DEFAULT_BATCH_SIZE, DEFAULT_MAX_DELAY_MILLIS);
    }

    public SpanExporter(String serviceName,
                        String instanceId,
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
        this.serviceName = Objects.requireNonNull(serviceName, "serviceName");
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
        this.spanSink = Objects.requireNonNull(spanSink, "spanSink");
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.batchSize = batchSize;
        this.maxDelayMillis = maxDelayMillis;
        this.writer = new ObjectMapper().writerWithDefaultPrettyPrinter();
    }

    @Override
    public void handle(Span span) {
        if (span == null || closed.get()) {
            droppedCount.incrementAndGet();
            return;
        }

        SpanRecord record = SpanRecord.from(span);
        if (queue.offer(record)) {
            acceptedCount.incrementAndGet();
        } else {
            droppedCount.incrementAndGet();
        }
    }

    public void start() {
        if (closed.get() || !started.compareAndSet(false, true)) {
            return;
        }

        Thread worker = new Thread(this::runWorker, "span-exporter-" + serviceName);
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
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        Thread worker = workerThread;
        if (worker != null) {
            worker.interrupt();
            try {
                worker.join(maxDelayMillis + 1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        flush();
        closeSink();
    }

    public long acceptedCount() {
        return acceptedCount.get();
    }

    public long exportedCount() {
        return exportedCount.get();
    }

    public long droppedCount() {
        return droppedCount.get();
    }

    public long failedCount() {
        return failedCount.get();
    }

    public int queueDepth() {
        return queue.size();
    }

    public boolean isStarted() {
        return started.get();
    }

    public boolean isClosed() {
        return closed.get();
    }

    private void runWorker() {
        try {
            while (!closed.get() && !Thread.currentThread().isInterrupted()) {
                exportNextBatch();
            }
        } finally {
            flush();
        }
    }

    private void exportNextBatch() {
        List<SpanRecord> batch = new ArrayList<>(batchSize);
        boolean interrupted = false;
        try {
            SpanRecord first = queue.poll(maxDelayMillis, TimeUnit.MILLISECONDS);
            if (first == null) {
                return;
            }

            batch.add(first);
            long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(maxDelayMillis);
            while (batch.size() < batchSize) {
                SpanRecord immediate = queue.poll();
                if (immediate != null) {
                    batch.add(immediate);
                    continue;
                }

                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) {
                    break;
                }

                SpanRecord next = queue.poll(remainingNanos, TimeUnit.NANOSECONDS);
                if (next == null) {
                    break;
                }
                batch.add(next);
            }
        } catch (InterruptedException e) {
            interrupted = true;
        }

        if (!batch.isEmpty()) {
            exportRecords(batch);
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void exportRecords(List<SpanRecord> records) {
        if (records.isEmpty()) {
            return;
        }

        synchronized (exportLock) {
            try {
                SpanExport export = new SpanExport(
                        serviceName,
                        instanceId,
                        System.currentTimeMillis(),
                        records);
                spanSink.send(writer.writeValueAsString(export));
                exportedCount.addAndGet(records.size());
            } catch (Exception e) {
                failedCount.addAndGet(records.size());
            }
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
