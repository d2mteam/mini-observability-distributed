package com.app.core.propagation;

import org.slf4j.MDC;

public class TraceContextHolder {
    private static final ThreadLocal<TraceContext> CURRENT = new ThreadLocal<>();

    public static TraceContext get() {
        return CURRENT.get();
    }

    public static void set(TraceContext traceContext) {
        CURRENT.set(traceContext);
    }

    public static Scope newScope(TraceContext context) {
        TraceContext previous = CURRENT.get();   // LƯU cũ — biến local, riêng mỗi lần gọi
        CURRENT.set(context);                    // ĐẶT mới
        if (context != null) {
            MDC.put("traceId", context.traceId());
            MDC.put("spanId", context.spanId());
        }
        return new Scope(previous);              // Scope nhớ previous CỦA RIÊNG NÓ
    }

    public record Scope(TraceContext previous) implements AutoCloseable {
        @Override
        public void close() {
            if (previous != null) {
                CURRENT.set(previous);                       // khôi phục cha
                MDC.put("traceId", previous.traceId());
                MDC.put("spanId", previous.spanId());
            } else {
                CURRENT.remove();                            // không có cha → xóa hẳn
                MDC.remove("traceId");
                MDC.remove("spanId");
            }
        }
    }
}