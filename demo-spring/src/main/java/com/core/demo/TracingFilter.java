package com.core.demo;

import com.core.metrics.MetricsRegistry;
import com.core.tracing.Span;
import com.core.tracing.Tracer;
import com.core.tracing.propagation.Propagator;
import com.core.tracing.propagation.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * INBOUND: một điểm cắm feed CẢ HAI hệ độc lập — Tracer (span) và MetricsRegistry (đếm).
 * extract trace context → SERVER span; đồng thời onRequestStart/End cho metrics.
 */
public class TracingFilter extends OncePerRequestFilter {
    private final Tracer tracer;
    private final Propagator propagator;
    private final MetricsRegistry metrics;

    public TracingFilter(Tracer tracer, Propagator propagator, MetricsRegistry metrics) {
        this.tracer = tracer;
        this.propagator = propagator;
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String endpoint = req.getMethod() + " " + req.getRequestURI();   // TODO: normalize route (cardinality)
        TraceContext parent = propagator.extract(req, HttpServletRequest::getHeader);   // Getter
        Span span = tracer.nextSpan(parent).name(endpoint).kind(Span.Kind.SERVER);

        metrics.onRequestStart(endpoint);
        long startNanos = System.nanoTime();
        boolean error = false;
        try (var ws = tracer.withSpanInScope(span)) {
            chain.doFilter(req, res);
            span.tag("http.status_code", String.valueOf(res.getStatus()));
        } catch (Exception e) {
            error = true;
            span.error(e);
            throw e;
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            tracer.finishSpan(span);                                              // → TRACE
            metrics.onRequestEnd(endpoint, durationMs, error, Math.max(0, req.getContentLengthLong())); // → METRIC
        }
    }
}
