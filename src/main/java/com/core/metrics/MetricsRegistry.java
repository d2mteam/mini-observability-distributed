package com.core.metrics;

/**
 * Điểm cắm metrics — interceptor/handler gọi vào. Tất cả method default no-op, nên một bare
 * {@code new MetricsRegistry(){}} chính là bản NOOP (tắt metrics qua config = dùng bản này).
 *
 * <p>Độc lập hoàn toàn với tracing: nhận PRIMITIVE (endpoint/duration/bytes...), không đụng Span.
 */
public interface MetricsRegistry {
    /** Bắt đầu một request → in-flight++. (endpoint chưa cần cho gauge global) */
    default void onRequestStart(String endpoint) {}

    /** Kết thúc request → count/error/slow/latency/bytes theo endpoint, in-flight--. */
    default void onRequestEnd(String endpoint, long durationMillis, boolean error, long bytes) {}

    /** Kết nối stateful (WS/TCP) mở tới endpoint → active-connections[endpoint]++. */
    default void onConnectionOpened(String endpoint) {}

    /** Kết nối stateful đóng → active-connections[endpoint]--. */
    default void onConnectionClosed(String endpoint) {}

    /** Kết quả một cú gọi OUTBOUND tới destination → consecutive-failure streak (reset khi ok). */
    default void onDestinationResult(String destination, boolean ok) {}

    /** Bản chụp hiện tại cho /metrics. */
    default MetricsSnapshot snapshot() {
        return MetricsSnapshot.empty();
    }
}
