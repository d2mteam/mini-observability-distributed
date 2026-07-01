package com.app.demo;

import com.app.core.Span;
import com.app.core.Tracer;
import com.app.core.propagation.Propagator;
import com.app.core.propagation.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * INBOUND: extract trace context từ header request đến → mở SERVER span nối tiếp trace upstream
 * (hoặc trace mới nếu không có header). Scope đóng tự động khi request xong.
 */
public class TracingFilter extends OncePerRequestFilter {
    private final Tracer tracer;
    private final Propagator propagator;

    public TracingFilter(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        TraceContext parent = propagator.extract(req, HttpServletRequest::getHeader);   // Getter
        Span span = tracer.nextSpan(parent).name(req.getMethod() + " " + req.getRequestURI()).kind(Span.Kind.SERVER);
        try (var ws = tracer.withSpanInScope(span)) {
            chain.doFilter(req, res);
            span.tag("http.status_code", String.valueOf(res.getStatus()));
        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            tracer.finishSpan(span);
        }
    }
}
