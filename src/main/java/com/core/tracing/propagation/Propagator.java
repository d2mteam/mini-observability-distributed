package com.core.tracing.propagation;

public class Propagator {
    private static final String TRACEPARENT = "traceparent";

    public interface Setter<C> {
        void put(C carrier, String key, String value);
    }

    public interface Getter<C> {
        String get(C carrier, String key);
    }

    public <C> void inject(TraceContext ctx, C carrier, Setter<C> setter) {
        if (ctx == null) return;
        String flags = ctx.sampled() ? "01" : "00";
        String traceparent = "00-" + ctx.traceId() + '-' + ctx.spanId() + '-' + flags;
        setter.put(carrier, TRACEPARENT, traceparent);
    }

    public <C> TraceContext extract(C carrier, Getter<C> getter) {
        String h = getter.get(carrier, TRACEPARENT);
        if (h == null) return null;
        String[] p = h.split("-");
        if (p.length != 4 || p[1].length() != 32 || p[2].length() != 16) return null;
        boolean sampled = (Integer.parseInt(p[3], 16) & 1) == 1;
        return new TraceContext(p[1], p[2], null, sampled);   // context CHA (span upstream) — Quy ước A
    }
}
