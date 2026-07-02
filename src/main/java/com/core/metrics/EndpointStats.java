package com.core.metrics;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Số liệu tích lũy của MỘT endpoint — nội bộ registry. Thread-safe: LongAdder cho counter,
 * AtomicLong cho gauge, HdrHistogram Recorder cho latency (record concurrent an toàn; đọc percentile
 * qua {@link #snapshot()} đã đồng bộ).
 */
class EndpointStats {
    private final LongAdder count = new LongAdder();
    private final LongAdder errors = new LongAdder();
    private final LongAdder slow = new LongAdder();
    private final LongAdder totalBytes = new LongAdder();
    private final AtomicLong activeConnections = new AtomicLong();

    // Recorder cho ghi concurrent; latencyTotal gom all-time, chỉ đụng trong snapshot() (synchronized).
    private final Recorder latencyRecorder = new Recorder(1, 3_600_000L, 3);   // 1ms .. 1h, 3 sig
    private final Histogram latencyTotal = new Histogram(1, 3_600_000L, 3);

    void record(long durationMillis, boolean error, long bytes, long slowThresholdMillis) {
        count.increment();
        if (error) errors.increment();
        if (durationMillis > slowThresholdMillis) slow.increment();
        if (bytes > 0) totalBytes.add(bytes);
        latencyRecorder.recordValue(Math.max(0, durationMillis));
    }

    void connectionOpened() {
        activeConnections.incrementAndGet();
    }

    void connectionClosed() {
        activeConnections.decrementAndGet();
    }

    synchronized MetricsSnapshot.Endpoint snapshot() {
        latencyTotal.add(latencyRecorder.getIntervalHistogram());   // gom phần mới vào all-time
        return new MetricsSnapshot.Endpoint(
                count.sum(), errors.sum(), slow.sum(), totalBytes.sum(), activeConnections.get(),
                latencyTotal.getValueAtPercentile(50),
                latencyTotal.getValueAtPercentile(95),
                latencyTotal.getValueAtPercentile(99));
    }
}
