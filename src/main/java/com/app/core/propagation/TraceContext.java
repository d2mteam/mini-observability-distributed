package com.app.core.propagation;

import com.app.internal.IdGenerator;
import lombok.AccessLevel;
import lombok.Builder;

@Builder(access = AccessLevel.PRIVATE)
public record TraceContext(String traceId, String spanId, String parentSpanId, boolean sampled) {
    public static TraceContext root(String traceId, boolean sampled) {
        return TraceContext.builder()
                .traceId(traceId)
                .spanId(IdGenerator.newSpanId())     // spanId mới
                .parentSpanId(null)                  // root → không cha
                .sampled(sampled)
                .build();
    }

    public TraceContext child() {
        return TraceContext.builder()
                .traceId(this.traceId)               // GIỮ traceId
                .spanId(IdGenerator.newSpanId())     // spanId MỚI
                .parentSpanId(this.spanId)           // cha = spanId hiện tại của mình
                .sampled(this.sampled)               // giữ quyết định sampling
                .build();
    }

    public static TraceContext fromRemote(String traceId, String parentSpanId, boolean sampled) {
        return TraceContext.builder()
                .traceId(traceId)                    // GIỮ traceId từ service gọi
                .spanId(IdGenerator.newSpanId())     // spanId MỚI cho chặng của mình
                .parentSpanId(parentSpanId)          // cha = spanId của upstream
                .sampled(sampled)                    // tôn trọng sampled từ upstream
                .build();
    }
}