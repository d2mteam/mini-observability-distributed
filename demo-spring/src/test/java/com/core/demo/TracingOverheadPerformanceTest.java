package com.core.demo;

import com.core.metrics.MetricsRegistry;
import com.core.tracing.Sampler.Sampler;
import com.core.tracing.SpanDispatcher;
import com.core.tracing.Tracer;
import com.core.tracing.handler.SpanHandler;
import com.core.tracing.propagation.Propagator;
import com.core.tracing.propagation.TraceContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("performance")
class TracingOverheadPerformanceTest {
    private static final URI CLIENT_URI = URI.create("http://inventory.local/items/42");
    private static final byte[] REQUEST_BODY = "hello".getBytes();
    private static final FilterChain SERVER_CHAIN = (request, response) ->
            ((HttpServletResponse) response).setStatus(204);
    private static final ClientHttpRequestExecution CLIENT_EXECUTION = (request, body) ->
            new MockClientHttpResponse(new byte[0], HttpStatus.OK);

    private static volatile int blackhole;
    private static final MetricsRegistry NOOP_METRICS = new MetricsRegistry() {};   // đo overhead tracing thuần

    private final int iterations = Integer.getInteger("perf.iterations", 30_000);
    private final int warmupIterations = Integer.getInteger("perf.warmupIterations", 5_000);

    @AfterEach
    void noContextLeak() {
        assertNull(TraceContextHolder.get(), "trace context leaked after performance test");
    }

    @Test
    void httpInboundFilter_overhead() throws Exception {
        TracingFilter alwaysSampled = new TracingFilter(tracer(Sampler.ALWAYS_SAMPLE), new Propagator(), NOOP_METRICS);
        TracingFilter neverSampled = new TracingFilter(tracer(Sampler.NEVER_SAMPLE), new Propagator(), NOOP_METRICS);

        Measurement baseline = measure("servlet baseline", this::bareServletCall);
        Measurement never = measure("filter tracing, never sample", () -> tracedServletCall(neverSampled));
        Measurement always = measure("filter tracing, always sample", () -> tracedServletCall(alwaysSampled));

        report("HTTP inbound filter", baseline, never, always);
        assertSane(baseline, never, always);
    }

    @Test
    void httpOutboundInterceptor_overhead() throws Exception {
        TracingClientInterceptor alwaysSampled =
                new TracingClientInterceptor(tracer(Sampler.ALWAYS_SAMPLE), new Propagator(), NOOP_METRICS);
        TracingClientInterceptor neverSampled =
                new TracingClientInterceptor(tracer(Sampler.NEVER_SAMPLE), new Propagator(), NOOP_METRICS);

        Measurement baseline = measure("client baseline", this::bareClientCall);
        Measurement never = measure("client tracing, never sample", () -> tracedClientCall(neverSampled));
        Measurement always = measure("client tracing, always sample", () -> tracedClientCall(alwaysSampled));

        report("HTTP outbound interceptor", baseline, never, always);
        assertSane(baseline, never, always);
    }

    private Tracer tracer(Sampler sampler) {
        SpanHandler sink = span -> blackhole ^= span.getSpanId().hashCode();
        return new Tracer("demo-spring-perf", new SpanDispatcher(List.of(sink)), sampler);
    }

    private void bareServletCall() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders/42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        SERVER_CHAIN.doFilter(request, response);
        blackhole ^= response.getStatus();
    }

    private void tracedServletCall(TracingFilter filter) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders/42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, SERVER_CHAIN);
        blackhole ^= response.getStatus();
    }

    private void bareClientCall() throws Exception {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, CLIENT_URI);
        try (ClientHttpResponse response = CLIENT_EXECUTION.execute(request, REQUEST_BODY)) {
            blackhole ^= response.getStatusCode().value();
        }
    }

    private void tracedClientCall(TracingClientInterceptor interceptor) throws Exception {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, CLIENT_URI);
        try (ClientHttpResponse response = interceptor.intercept(request, REQUEST_BODY, CLIENT_EXECUTION)) {
            blackhole ^= response.getStatusCode().value();
            blackhole ^= request.getHeaders().size();
        }
    }

    private Measurement measure(String name, ThrowingRunnable action) throws Exception {
        assertTrue(iterations > 0, "perf.iterations must be positive");
        assertTrue(warmupIterations >= 0, "perf.warmupIterations must be non-negative");

        for (int i = 0; i < warmupIterations; i++) {
            action.run();
        }

        forceGc();
        long allocatedBefore = allocatedBytes();
        long retainedBefore = usedMemory();
        long started = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            action.run();
        }

        long elapsedNanos = System.nanoTime() - started;
        long allocatedAfter = allocatedBytes();
        forceGc();
        long retainedAfter = usedMemory();

        long allocated = allocatedBefore >= 0 && allocatedAfter >= 0 ? allocatedAfter - allocatedBefore : -1;
        return new Measurement(name, iterations, elapsedNanos, allocated, retainedAfter - retainedBefore);
    }

    private static void report(String title, Measurement baseline, Measurement... measurements) {
        System.out.println();
        System.out.println("== " + title + " ==");
        System.out.println(baseline.format(null));
        for (Measurement measurement : measurements) {
            System.out.println(measurement.format(baseline));
        }
        blackhole ^= title.length();
    }

    private static void assertSane(Measurement... measurements) {
        for (Measurement measurement : measurements) {
            assertTrue(measurement.elapsedNanos > 0, measurement.name + " elapsed time must be positive");
            assertTrue(measurement.nanosPerOp() > 0, measurement.name + " ns/op must be positive");
        }
    }

    private static long allocatedBytes() {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (!(bean instanceof com.sun.management.ThreadMXBean allocationBean)) {
            return -1;
        }
        if (!allocationBean.isThreadAllocatedMemorySupported()) {
            return -1;
        }
        if (!allocationBean.isThreadAllocatedMemoryEnabled()) {
            allocationBean.setThreadAllocatedMemoryEnabled(true);
        }
        return allocationBean.getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    private static long usedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void forceGc() throws InterruptedException {
        System.gc();
        Thread.sleep(30);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private record Measurement(String name, int iterations, long elapsedNanos,
                               long allocatedBytes, long retainedBytes) {
        double nanosPerOp() {
            return elapsedNanos / (double) iterations;
        }

        double allocatedBytesPerOp() {
            return allocatedBytes < 0 ? -1 : allocatedBytes / (double) iterations;
        }

        String format(Measurement baseline) {
            String allocated = allocatedBytes < 0
                    ? "n/a"
                    : formatNumber(allocatedBytesPerOp());
            String base = String.format(Locale.ROOT,
                    "%-31s %12s ns/op %12s B/op retained %+9d B",
                    name, formatNumber(nanosPerOp()), allocated, retainedBytes);
            if (baseline == null) {
                return base;
            }

            double timeOverhead = nanosPerOp() - baseline.nanosPerOp();
            double allocationOverhead = allocatedBytesPerOp() < 0 || baseline.allocatedBytesPerOp() < 0
                    ? -1
                    : allocatedBytesPerOp() - baseline.allocatedBytesPerOp();
            String allocationText = allocationOverhead < 0 ? "n/a" : formatNumber(allocationOverhead);
            return base + String.format(Locale.ROOT,
                    " | overhead %10s ns/op %10s B/op x%.2f",
                    formatNumber(timeOverhead), allocationText, nanosPerOp() / baseline.nanosPerOp());
        }

        private static String formatNumber(double value) {
            return String.format(Locale.ROOT, "%,.1f", value);
        }
    }
}
