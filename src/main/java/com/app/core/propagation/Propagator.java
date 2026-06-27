package com.app.core.propagation;


public class Propagator {
    public interface Setter<C> {
        void put(C carrier, String key, String value);
    }
    public interface Getter<C> {
        String get(C carrier, String key);
    }

    public <C> void inject(TraceContext ctx, C carrier, Setter<C> setter) {

    }

    public <C> TraceContext extract(C carrier, Getter<C> getter) {
        // parse "00-{traceId}-{parentSpanId}-{flags}"
        return null;
    }
}
