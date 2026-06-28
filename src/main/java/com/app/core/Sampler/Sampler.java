package com.app.core.Sampler;

import java.security.SecureRandom;

public abstract class Sampler {
    public abstract boolean isSampled(String traceId);

    public static final Sampler ALWAYS_SAMPLE = new Sampler() {
        @Override
        public boolean isSampled(String traceId) {
            return true;
        }

        @Override
        public String toString() {
            return "AlwaysSample";
        }
    };

    public static final Sampler NEVER_SAMPLE = new Sampler() {
        @Override
        public boolean isSampled(String traceId) {
            return false;
        }

        @Override
        public String toString() {
            return "NeverSample";
        }
    };

    /**
     * Lấy mẫu theo tỉ lệ, quyết định dựa trên chính traceId — cùng một traceId luôn cho cùng
     * kết quả, nên nhất quán xuyên suốt trace (kể cả khi re-derive ở service khác).
     *
     * @param rate trong [0.0, 1.0]; 0.0 → {@link #NEVER_SAMPLE}, 1.0 → {@link #ALWAYS_SAMPLE}.
     */
    public static Sampler create(float rate) {
        if (rate < 0.0f || rate > 1.0f) {
            throw new IllegalArgumentException("rate phải trong [0.0, 1.0], nhận: " + rate);
        }
        if (rate == 0.0f) return NEVER_SAMPLE;
        if (rate == 1.0f) return ALWAYS_SAMPLE;
        return new ProbabilisticSampler(rate);
    }

    private static final class ProbabilisticSampler extends Sampler {
        // SALT ngẫu nhiên 1 lần/JVM, trộn vào id (giống Brave BoundarySampler) để quyết định
        // không đoán/ép được từ traceId. Quyết định/trace vẫn nhất quán vì chỉ chốt 1 lần ở root.
        private static final long SALT = new SecureRandom().nextLong();

        private final float rate;
        private final long boundary;   // rate * 10000

        ProbabilisticSampler(float rate) {
            this.rate = rate;
            this.boundary = (long) (rate * 10_000);
        }

        @Override
        public boolean isSampled(String traceId) {
            // id vốn random (SecureRandom 128-bit) → modulo cho phân phối đều ≈ rate
            return boundedValue(traceId) % 10_000L < boundary;
        }

        /** Lấy 64 bit cuối của traceId hex, XOR salt rồi xóa bit dấu → số không âm, đều. */
        private static long boundedValue(String traceId) {
            long id;
            if (traceId != null && traceId.length() >= 16) {
                try {
                    id = Long.parseUnsignedLong(traceId.substring(traceId.length() - 16), 16);
                } catch (NumberFormatException e) {
                    id = traceId.hashCode();
                }
            } else {
                id = traceId == null ? 0 : traceId.hashCode();
            }
            return (id ^ SALT) & Long.MAX_VALUE;
        }

        @Override
        public String toString() {
            return "ProbabilisticSampler{rate=" + rate + "}";
        }
    }
}
