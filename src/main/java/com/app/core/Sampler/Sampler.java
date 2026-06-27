package com.app.core.Sampler;

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

}
