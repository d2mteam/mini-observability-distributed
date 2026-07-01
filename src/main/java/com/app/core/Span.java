package com.app.core;

import lombok.Builder;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

public class Span {
    public enum Kind { CLIENT, SERVER, PRODUCER, CONSUMER, INTERNAL }
    public enum Status { UNSET, OK, ERROR }

    @Getter
    private final String traceId;

    @Getter
    private final String spanId;

    @Getter
    private final String parentSpanId;

    @Getter
    private String name;

    @Getter
    private Kind kind;

    @Getter
    private final String serviceName;

    @Getter
    private final long startEpochMillis;

    @Getter
    private final boolean sampled;

    @Getter
    private long durationMillis;

    @Getter
    private Status status = Status.UNSET;

    @Getter
    private final Map<String, String> attributes = new HashMap<>();

    @Getter
    private boolean finished = false;

    @Builder
    public Span(String traceId, String spanId, String parentSpanId,
                String name, Kind kind, String serviceName, long startEpochMillis, boolean sampled) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.name = name;
        this.kind = kind;
        this.serviceName = serviceName;
        this.startEpochMillis = startEpochMillis;
        this.sampled = sampled;
    }

    // ---- mutator adapter dùng khi span đang chạy ----
    public Span name(String name)     { if (!finished) this.name = name;     return this; }
    public Span kind(Kind kind)       { if (!finished) this.kind = kind;     return this; }
    public Span status(Status status) { if (!finished) this.status = status; return this; }

    public Span tag(String key, String value) {
        if (!finished && key != null && value != null) attributes.put(key, value);
        return this;
    }

    public Span error(Throwable t) {
        if (!finished) {
            this.status = Status.ERROR;
            if (t != null) {
                attributes.put("error", t.getClass().getSimpleName());
                if (t.getMessage() != null) attributes.put("error.message", t.getMessage());
            }
        }
        return this;
    }

    /**
     * Chốt duration/status — chỉ chạm field của chính mình (Span là dữ liệu thuần, không record).
     * Package-private: chỉ {@link Tracer} gọi qua {@link Tracer#finishSpan(Span)}. Idempotent.
     */
    void end(long endEpochMillis) {
        if (finished) return;
        if (status == Status.UNSET) status = Status.OK;
        this.durationMillis = endEpochMillis - startEpochMillis;
        this.finished = true;
    }
}