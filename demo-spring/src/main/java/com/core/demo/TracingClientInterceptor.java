package com.core.demo;

import com.core.metrics.MetricsRegistry;
import com.core.tracing.Span;
import com.core.tracing.Tracer;
import com.core.tracing.propagation.Propagator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

public class TracingClientInterceptor implements ClientHttpRequestInterceptor {
    private final Tracer tracer;
    private final Propagator propagator;
    private final MetricsRegistry metrics;

    public TracingClientInterceptor(Tracer tracer, Propagator propagator, MetricsRegistry metrics) {
        this.tracer = tracer;
        this.propagator = propagator;
        this.metrics = metrics;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution exec)
            throws IOException {
//        String endpoint = request.getMethod() + " " + request.getURI(); /placeholder

        String endpoint = "HTTP";
        String host = request.getURI().getHost();
        String destination = host != null ? host : "unknown";
        Span span = tracer.nextSpan().name(endpoint).kind(Span.Kind.CLIENT)
                .tag("protocol", "http")
                .tag("server.address", destination)
                .tag("http.request.size", String.valueOf(body.length));

        metrics.onRequestStart();
        long startNanos = System.nanoTime();
        boolean error = false;
        try (var ws = tracer.withSpanInScope(span)) {
            propagator.inject(tracer.currentContext(), request.getHeaders(), HttpHeaders::add);
            ClientHttpResponse res = exec.execute(request, body);
            span.tag("http.status_code", String.valueOf(res.getStatusCode().value()));
            return res;
        } catch (IOException | RuntimeException e) {
            error = true;
            span.error(e);
            throw e;
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            tracer.finishSpan(span);
            metrics.onRequestEnd(endpoint, durationMs, error, body.length);
            metrics.onDestinationResult(destination, !error);
        }
    }

    // String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);

}
