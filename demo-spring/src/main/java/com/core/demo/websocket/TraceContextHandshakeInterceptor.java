package com.core.demo.websocket;

import com.core.tracing.propagation.Propagator;
import com.core.tracing.propagation.TraceContext;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

public class TraceContextHandshakeInterceptor implements HandshakeInterceptor {
    public static final String TRACE_CONTEXT_ATTRIBUTE = "mini.traceContext";

    private final Propagator propagator;

    public TraceContextHandshakeInterceptor(Propagator propagator) {
        this.propagator = propagator;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        TraceContext context = propagator.extract(request.getHeaders(), (headers, key) -> headers.getFirst(key));
        if (context != null) {
            attributes.put(TRACE_CONTEXT_ATTRIBUTE, context);
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }
}
