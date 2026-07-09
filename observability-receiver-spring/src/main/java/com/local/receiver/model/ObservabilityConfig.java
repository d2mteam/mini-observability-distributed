package com.local.receiver.model;

public record ObservabilityConfig(Interceptors interceptors,
                                  Tracing tracing,
                                  Metrics metrics,
                                  Output output) {
    public ObservabilityConfig {
        interceptors = interceptors == null ? Interceptors.defaults() : interceptors;
        tracing = tracing == null ? Tracing.defaults() : tracing;
        metrics = metrics == null ? Metrics.defaults() : metrics;
        output = output == null ? Output.defaults() : output;
    }

    public static ObservabilityConfig defaults() {
        return new ObservabilityConfig(
                Interceptors.defaults(),
                Tracing.defaults(),
                Metrics.defaults(),
                Output.defaults()
        );
    }

    public record Interceptors(boolean http,
                               boolean jdbc,
                               boolean websocket,
                               boolean rsocket) {
        public static Interceptors defaults() {
            return new Interceptors(true, true, true, true);
        }
    }

    public record Tracing(double samplingRate) {
        public static Tracing defaults() {
            return new Tracing(1.0);
        }
    }

    public record Metrics(long slowThresholdMillis) {
        public static Metrics defaults() {
            return new Metrics(500);
        }
    }

    public record Output(String traceSink,
                         String metricsMode,
                         boolean aiContextEnabled) {
        public static Output defaults() {
            return new Output("http", "push", true);
        }
    }
}
