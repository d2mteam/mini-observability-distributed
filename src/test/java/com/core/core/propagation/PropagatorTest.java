package com.core.core.propagation;

import com.core.tracing.propagation.Propagator;
import com.core.tracing.propagation.TraceContext;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropagatorTest {

    private final Propagator propagator = new Propagator();

    @Test
    void injectThenExtractRoundTrip() {
        TraceContext ctx =
                new TraceContext("0af7651916cd43dd8448eb211c80319c", "b7ad6b7169203331", null, true);
        Map<String, String> carrier = new HashMap<>();

        propagator.inject(ctx, carrier, (m, k, v) -> m.put(k, v));
        assertEquals("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                carrier.get("traceparent"));

        TraceContext extracted = propagator.extract(carrier, (m, k) -> m.get(k));
        assertNotNull(extracted);
        assertEquals(ctx.traceId(), extracted.traceId());
        assertEquals(ctx.spanId(), extracted.spanId());   // parent-id trên dây = spanId bên gửi
        assertTrue(extracted.sampled());
    }

    @Test
    void notSampledFlagEncodedAndDecoded() {
        TraceContext ctx =
                new TraceContext("0af7651916cd43dd8448eb211c80319c", "b7ad6b7169203331", null, false);
        Map<String, String> carrier = new HashMap<>();

        propagator.inject(ctx, carrier, (m, k, v) -> m.put(k, v));
        assertTrue(carrier.get("traceparent").endsWith("-00"));

        assertFalse(propagator.extract(carrier, (m, k) -> m.get(k)).sampled());
    }

    @Test
    void noHeaderReturnsNull() {
        assertNull(propagator.extract(new HashMap<String, String>(), (m, k) -> m.get(k)));
    }

    @Test
    void malformedHeaderReturnsNull() {
        Map<String, String> carrier = new HashMap<>();
        carrier.put("traceparent", "rác-không-đúng-format");
        assertNull(propagator.extract(carrier, (m, k) -> m.get(k)));
    }
}
