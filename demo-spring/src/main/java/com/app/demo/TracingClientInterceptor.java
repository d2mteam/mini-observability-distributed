package com.app.demo;

import com.app.core.Span;
import com.app.core.Tracer;
import com.app.core.propagation.Propagator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * OUTBOUND: mở CLIENT span quanh cú gọi đi, inject context hiện tại vào header request
 * → downstream extract được và nối tiếp cùng trace.
 */
public class TracingClientInterceptor implements ClientHttpRequestInterceptor {
    private final Tracer tracer;
    private final Propagator propagator;

    public TracingClientInterceptor(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution exec)
            throws IOException {
        Span span = tracer.nextSpan().name(request.getMethod() + " " + request.getURI()).kind(Span.Kind.CLIENT);
        try (var ws = tracer.withSpanInScope(span)) {
            // inject SAU khi scope: currentContext() chính là CLIENT span này → downstream parent về nó
            propagator.inject(tracer.currentContext(), request.getHeaders(), HttpHeaders::add);   // Setter
            ClientHttpResponse res = exec.execute(request, body);
            span.tag("http.status_code", String.valueOf(res.getStatusCode().value()));
            return res;
        } catch (IOException | RuntimeException e) {
            span.error(e);
            throw e;
        } finally {
            tracer.finishSpan(span);
        }
    }
}
