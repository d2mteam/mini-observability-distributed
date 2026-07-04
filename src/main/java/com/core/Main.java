package com.core;

import com.core.export.ConsoleExportSink;
import com.core.export.MetricsPushExporter;
import com.core.export.ServiceIdentity;
import com.core.metrics.MetricsConfig;
import com.core.metrics.SimpleMetricsRegistry;
import com.core.tracing.Sampler.Sampler;
import com.core.tracing.Span;
import com.core.tracing.SpanDispatcher;
import com.core.tracing.Tracer;
import com.core.tracing.handler.SpanHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Main {
    public static void main(String[] args) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        SpanHandler printer = span -> {
            try {
                System.out.printf("[SPAN] %s%n", objectMapper.writeValueAsString(span));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        };

        // =================== TRACING demo (sync + async) ===================
        Tracer tracer = new Tracer("main", new SpanDispatcher(List.of(printer)), Sampler.ALWAYS_SAMPLE);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        ExecutorService callbackPool = Executors.newSingleThreadExecutor();

        Span root = tracer.nextSpan().name("handle-request").kind(Span.Kind.SERVER);
        try (var ignored = tracer.withSpanInScope(root)) {
            Span validate = tracer.nextSpan().name("validate").kind(Span.Kind.INTERNAL);
            try (var vs = tracer.withSpanInScope(validate)) {
                validate.tag("thread", Thread.currentThread().getName());
            } finally {
                tracer.finishSpan(validate);
            }

            Span dbSpan = tracer.nextSpan().name("query-db").kind(Span.Kind.CLIENT);
            Future<String> f = pool.submit(() -> {
                try (var s = tracer.withSpanInScope(dbSpan)) {
                    dbSpan.tag("thread", Thread.currentThread().getName());
                    return "users";
                } finally {
                    tracer.finishSpan(dbSpan);
                }
            });

            Span redisSpan = tracer.nextSpan().name("call-redis").kind(Span.Kind.CLIENT);
            CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> {
                try (var s = tracer.withSpanInScope(redisSpan)) {
                    redisSpan.tag("work.thread", Thread.currentThread().getName());
                    return "cached";
                }
            }, pool).whenCompleteAsync((res, err) -> {
                if (err != null) redisSpan.error(err);
                tracer.finishSpan(redisSpan);
            }, callbackPool);

            String db = f.get();
            String cache = cf.join();

            Span render = tracer.nextSpan().name("render").kind(Span.Kind.INTERNAL);
            try (var rs = tracer.withSpanInScope(render)) {
                render.tag("result", db + "+" + cache);
            } finally {
                tracer.finishSpan(render);
            }
        } finally {
            tracer.finishSpan(root);
        }
        pool.shutdown();
        callbackPool.shutdown();

        // =================== METRICS demo (độc lập với tracing) ===================
        var metrics = new SimpleMetricsRegistry(new MetricsConfig(2));   // slow > 2ms

        for (int i = 0; i < 100; i++) {
            metrics.onRequestStart();
            long durationMs = (long) (Math.random() * 5);   // 0..4 ms
            boolean error = i % 20 == 0;                     // ~5% lỗi
            metrics.onRequestEnd("GET /users", durationMs, error, 512);
        }
        metrics.onConnectionOpened("/ws/chat");
        metrics.onConnectionOpened("/ws/chat");
        metrics.onConnectionClosed("/ws/chat");
        metrics.onDestinationResult("service-b", false);
        metrics.onDestinationResult("service-b", false);    // streak = 2
        metrics.onDestinationResult("mysql", true);

        System.out.println("\n=== METRICS SNAPSHOT ===");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(metrics.snapshot()));


        MetricsPushExporter metricsPushExporter = MetricsPushExporter.builder()
                .serviceIdentity(ServiceIdentity.create("demo-main"))
                .exportSink(new ConsoleExportSink())
                .metricsRegistry(metrics)
                .build();

        metricsPushExporter.flush();
    }
}
