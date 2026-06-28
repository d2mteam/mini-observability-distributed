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
        try (var client = tracer.startSpan(request.getMethod() + " " + request.getURI(), Span.Kind.CLIENT)) {
            // inject SAU startSpan: currentContext() chính là CLIENT span này → downstream parent về nó
            propagator.inject(tracer.currentContext(), request.getHeaders(), HttpHeaders::add);   // Setter
            ClientHttpResponse res = exec.execute(request, body);
            client.span().tag("http.status_code", String.valueOf(res.getStatusCode().value()));
            return res;
        }
    }
}
