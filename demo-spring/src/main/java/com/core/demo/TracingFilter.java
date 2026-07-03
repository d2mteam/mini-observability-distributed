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
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;

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
        TraceContext parent = propagator.extract(req, HttpServletRequest::getHeader);
        Span span = tracer.nextSpan(parent).kind(Span.Kind.SERVER);

        metrics.onRequestStart();
        long startNanos = System.nanoTime();
        boolean error = false;
        try (var ws = tracer.withSpanInScope(span)) {
            chain.doFilter(req, res);
        } catch (Exception e) {
            error = true;
            span.error(e);
            throw e;
        } finally {
            String endpoint = endpoint(req);
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            span.tag("http.status_code", String.valueOf(res.getStatus())).name(endpoint);
            tracer.finishSpan(span);
            metrics.onRequestEnd(endpoint, durationMs, error, Math.max(0, req.getContentLengthLong()));
        }
    }

    private static String endpoint(HttpServletRequest req) {
        Object pattern = req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return req.getMethod() + " " + (pattern != null ? pattern : req.getRequestURI());
    }
}
