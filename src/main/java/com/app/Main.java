package com.app;

import com.app.core.Span;
import com.app.core.SpanDispatcher;
import com.app.core.Tracer;
import com.app.core.handler.SpanHandler;
import com.app.core.propagation.TraceContextHolder;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // handler in span ra console — điểm quan sát khi debug
        SpanHandler printer = span ->
                System.out.printf("[SPAN] trace=%s span=%s parent=%s name=%s kind=%s dur=%dms status=%s%n",
                        span.getTraceId(), span.getSpanId(), span.getParentSpanId(),
                        span.getName(), span.getKind(), span.getDurationMillis(), span.getStatus());

        List<SpanHandler> handlers = new ArrayList<>();
        handlers.add(printer);

        SpanDispatcher dispatcher = new SpanDispatcher(handlers);
        Tracer tracer = new Tracer("main", dispatcher);   // (dispatcher, serviceName)

        System.out.println("context sau root finish: " + TraceContextHolder.get());  // null

        try (var root2 = tracer.startSpan2("handle-request", Span.Kind.SERVER)) {
            try (var child22 = tracer.startSpan2("query-db", Span.Kind.CLIENT)) {
                child22.span().tag("db.statement", "SELECT * FROM users");
            }
        }
    }
}