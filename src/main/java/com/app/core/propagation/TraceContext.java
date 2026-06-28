package com.app.core.propagation;

/**
 * Dữ liệu trace bất biến của MỘT span — chỉ chứa field, không tự sinh id, không tự "tiến hop".
 * Mọi việc tạo/biến đổi context do {@link com.app.core.Tracer} kiểm soát (single owner);
 * {@code Propagator} chỉ decode header thành context CHA rồi đưa cho Tracer mint span kế tiếp.
 */
public record TraceContext(String traceId, String spanId, String parentSpanId, boolean sampled) {
}
