package com.core.demo.websocket;

import com.core.tracing.Span;
import com.core.tracing.Tracer;
import com.core.tracing.propagation.Propagator;
import com.core.tracing.propagation.TraceContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class StompTracingChannelInterceptor implements ExecutorChannelInterceptor {
    private final Tracer tracer;
    private final Propagator propagator;
    private final ThreadLocal<ActiveSpan> activeSpan = new ThreadLocal<>();

    public StompTracingChannelInterceptor(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        rememberConnectTraceContext(accessor);
        if (accessor.getCommand() != StompCommand.SEND) {
            return message;
        }

        String destination = destination(accessor);
        Span span = tracer.nextSpan(parentContext(accessor))
                .name("WS SEND " + destination)
                .kind(Span.Kind.SERVER)
                .tag("protocol", "websocket")
                .tag("messaging.system", "stomp")
                .tag("messaging.operation", "send")
                .tag("messaging.destination", destination)
                .tag("websocket.session_id", safe(accessor.getSessionId()));

        long payloadBytes = payloadBytes(message.getPayload());
        if (payloadBytes >= 0) {
            span.tag("messaging.message.payload_size_bytes", String.valueOf(payloadBytes));
        }

        activeSpan.set(new ActiveSpan(span, tracer.withSpanInScope(span)));
        return message;
    }

    @Override
    public void afterMessageHandled(Message<?> message,
                                    MessageChannel channel,
                                    MessageHandler handler,
                                    Exception ex) {
        ActiveSpan active = activeSpan.get();
        if (active == null) {
            return;
        }

        try {
            if (ex != null) {
                active.span().error(ex);
            }
        } finally {
            active.scope().close();
            tracer.finishSpan(active.span());
            activeSpan.remove();
        }
    }

    private void rememberConnectTraceContext(StompHeaderAccessor accessor) {
        if (accessor.getCommand() != StompCommand.CONNECT) {
            return;
        }
        TraceContext context = propagator.extract(accessor, (headers, key) -> headers.getFirstNativeHeader(key));
        if (context == null) {
            return;
        }
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            sessionAttributes.put(TraceContextHandshakeInterceptor.TRACE_CONTEXT_ATTRIBUTE, context);
        }
    }

    private static TraceContext parentContext(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return null;
        }
        Object context = sessionAttributes.get(TraceContextHandshakeInterceptor.TRACE_CONTEXT_ATTRIBUTE);
        return context instanceof TraceContext traceContext ? traceContext : null;
    }

    private static String destination(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        return destination == null || destination.isBlank() ? "unknown" : destination;
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static long payloadBytes(Object payload) {
        if (payload instanceof byte[] bytes) {
            return bytes.length;
        }
        if (payload instanceof String text) {
            return text.getBytes(StandardCharsets.UTF_8).length;
        }
        return -1;
    }

    private record ActiveSpan(Span span, Tracer.SpanInScope scope) {
    }
}
