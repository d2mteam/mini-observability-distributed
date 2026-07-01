package com.app;

import com.app.core.Sampler.Sampler;
import com.app.core.Span;
import com.app.core.SpanDispatcher;
import com.app.core.Tracer;
import com.app.core.handler.SpanHandler;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Main {
    public static void main(String[] args) throws Exception {
        SpanHandler printer = span -> System.out.printf(
                "[SPAN] trace=%s span=%s parent=%s name=%-10s dur=%3dms attrs=%s%n",
                span.getTraceId(), span.getSpanId(), span.getParentSpanId(),
                span.getName(), span.getDurationMillis(), span.getAttributes());
        Tracer tracer = new Tracer("main", new SpanDispatcher(List.of(printer)), Sampler.ALWAYS_SAMPLE);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        ExecutorService callbackPool = Executors.newSingleThreadExecutor();

        Span root = tracer.nextSpan().name("handle-request").kind(Span.Kind.SERVER);
        try (var ignored = tracer.withSpanInScope(root)) {

            // ---- SYNC: làm inline trên thread request (tạo + scope + finish cùng main) ----
            Span validate = tracer.nextSpan().name("validate").kind(Span.Kind.INTERNAL);
            try (var vs = tracer.withSpanInScope(validate)) {
                validate.tag("thread", Thread.currentThread().getName());
            } finally {
                tracer.finishSpan(validate);
            }

            // ---- ASYNC: offload ra worker (tạo ở main → parent=root; scope+finish ở worker) ----
            Span dbSpan = tracer.nextSpan().name("query-db").kind(Span.Kind.CLIENT);
            Future<String> f = pool.submit(() -> {
                try (var s = tracer.withSpanInScope(dbSpan)) {
                    dbSpan.tag("thread", Thread.currentThread().getName());
                    return "users";
                } finally {
                    tracer.finishSpan(dbSpan);
                }
            });

            // FINISH ở thread KHÁC thread làm việc: work ở pool, finish ở callbackPool (continuation)
            Span redisSpan = tracer.nextSpan().name("call-redis").kind(Span.Kind.CLIENT);
            CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> {
                try (var s = tracer.withSpanInScope(redisSpan)) {       // scope ở thread work
                    redisSpan.tag("work.thread", Thread.currentThread().getName());
                    return "cached";
                }                                                       // pop scope — CHƯA finish
            }, pool).whenCompleteAsync((res, err) -> {
                if (err != null) redisSpan.error(err);
                redisSpan.tag("finish.thread", Thread.currentThread().getName());
                tracer.finishSpan(redisSpan);                           // finish ở thread KHÁC
            }, callbackPool);

            String db = f.get();
            String cache = cf.join();

            // ---- SYNC: quay lại thread request, dùng kết quả async ----
            Span render = tracer.nextSpan().name("render").kind(Span.Kind.INTERNAL);
            try (var rs = tracer.withSpanInScope(render)) {
                render.tag("thread", Thread.currentThread().getName());
                render.tag("result", db + "+" + cache);
            } finally {
                tracer.finishSpan(render);
            }

        } finally {
            tracer.finishSpan(root);
        }
        pool.shutdown();
        callbackPool.shutdown();
    }
}
