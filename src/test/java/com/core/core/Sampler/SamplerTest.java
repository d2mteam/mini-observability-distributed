package com.core.core.Sampler;

import com.core.tracing.IdGenerator;
import com.core.tracing.Sampler.Sampler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SamplerTest {

    @Test
    void alwaysAndNever() {
        assertTrue(Sampler.ALWAYS_SAMPLE.isSampled(IdGenerator.newTraceId()));
        assertFalse(Sampler.NEVER_SAMPLE.isSampled(IdGenerator.newTraceId()));
    }

    @Test
    void createEdgesAndValidation() {
        assertSame(Sampler.NEVER_SAMPLE, Sampler.create(0.0f));
        assertSame(Sampler.ALWAYS_SAMPLE, Sampler.create(1.0f));
        assertThrows(IllegalArgumentException.class, () -> Sampler.create(1.5f));
        assertThrows(IllegalArgumentException.class, () -> Sampler.create(-0.1f));
    }

    @Test
    void rateIsApproximatelyHonoured() {
        Sampler s = Sampler.create(0.25f);
        int n = 200_000, hit = 0;
        for (int i = 0; i < n; i++) {
            if (s.isSampled(IdGenerator.newTraceId())) hit++;
        }
        double rate = (double) hit / n;
        assertTrue(rate > 0.23 && rate < 0.27, "rate ngoài kỳ vọng: " + rate);
    }

    @Test
    void sameTraceIdGivesSameDecision() {
        Sampler s = Sampler.create(0.5f);
        String traceId = IdGenerator.newTraceId();
        assertEquals(s.isSampled(traceId), s.isSampled(traceId));
    }
}
