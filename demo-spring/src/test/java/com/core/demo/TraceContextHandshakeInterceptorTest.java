package com.core.demo;

import com.core.demo.websocket.TraceContextHandshakeInterceptor;
import com.core.tracing.propagation.Propagator;
import com.core.tracing.propagation.TraceContext;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.ServerHttpAsyncRequestControl;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceContextHandshakeInterceptorTest {
    @Test
    void extractsTraceparentIntoWebSocketSessionAttributes() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");
        Map<String, Object> attributes = new HashMap<>();

        TraceContextHandshakeInterceptor interceptor = new TraceContextHandshakeInterceptor(new Propagator());
        boolean accepted = interceptor.beforeHandshake(new StubServerHttpRequest(headers), null, null, attributes);

        assertTrue(accepted);
        TraceContext context = (TraceContext) attributes.get(TraceContextHandshakeInterceptor.TRACE_CONTEXT_ATTRIBUTE);
        assertNotNull(context);
        assertEquals("0af7651916cd43dd8448eb211c80319c", context.traceId());
        assertEquals("b7ad6b7169203331", context.spanId());
        assertTrue(context.sampled());
    }

    private record StubServerHttpRequest(HttpHeaders headers) implements ServerHttpRequest {
        @Override
        public HttpMethod getMethod() {
            return HttpMethod.GET;
        }

        @Override
        public URI getURI() {
            return URI.create("http://localhost/ws/chat");
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Map.of();
        }

        @Override
        public InputStream getBody() throws IOException {
            return InputStream.nullInputStream();
        }

        @Override
        public Principal getPrincipal() {
            return null;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public ServerHttpAsyncRequestControl getAsyncRequestControl(ServerHttpResponse response) {
            throw new UnsupportedOperationException("not needed in this test");
        }
    }
}
