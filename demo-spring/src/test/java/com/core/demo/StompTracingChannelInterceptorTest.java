package com.core.demo;

import com.core.demo.websocket.StompTracingChannelInterceptor;
import com.core.demo.websocket.TraceContextHandshakeInterceptor;
import com.core.tracing.Sampler.Sampler;
import com.core.tracing.Span;
import com.core.tracing.SpanDispatcher;
import com.core.tracing.Tracer;
import com.core.tracing.handler.SpanHandler;
import com.core.tracing.propagation.Propagator;
import com.core.tracing.propagation.TraceContext;
import com.core.tracing.propagation.TraceContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StompTracingChannelInterceptorTest {
    private final List<Span> recorded = new ArrayList<>();
    private final Tracer tracer = new Tracer(new SpanDispatcher(List.of((SpanHandler) recorded::add)), Sampler.ALWAYS_SAMPLE);
    private final StompTracingChannelInterceptor interceptor =
            new StompTracingChannelInterceptor(tracer, new Propagator());

    @AfterEach
    void noContextLeak() {
        assertNull(TraceContextHolder.get());
    }

    @Test
    void sendMessageCreatesServerSpanFromSessionContext() {
        TraceContext parent = new TraceContext("0af7651916cd43dd8448eb211c80319c", "b7ad6b7169203331", null, true);
        Map<String, Object> session = new HashMap<>();
        session.put(TraceContextHandshakeInterceptor.TRACE_CONTEXT_ATTRIBUTE, parent);
        Message<byte[]> message = sendMessage(session, "/app/chat.send", "hello");

        interceptor.beforeHandle(message, null, null);
        assertEquals(parent.traceId(), TraceContextHolder.get().traceId());
        interceptor.afterMessageHandled(message, null, null, null);

        assertEquals(1, recorded.size());
        Span span = recorded.get(0);
        assertEquals(parent.traceId(), span.getTraceId());
        assertEquals(parent.spanId(), span.getParentSpanId());
        assertEquals("WS SEND /app/chat.send", span.getName());
        assertEquals(Span.Kind.SERVER, span.getKind());
        assertEquals(Span.Status.OK, span.getStatus());
        assertEquals("websocket", span.getAttributes().get("protocol"));
        assertEquals("stomp", span.getAttributes().get("messaging.system"));
        assertEquals("send", span.getAttributes().get("messaging.operation"));
        assertEquals("/app/chat.send", span.getAttributes().get("messaging.destination"));
        assertEquals("s1", span.getAttributes().get("websocket.session_id"));
        assertEquals("5", span.getAttributes().get("messaging.message.payload_size_bytes"));
    }

    @Test
    void connectNativeTraceparentOverridesExistingSessionContext() {
        Map<String, Object> session = new HashMap<>();
        session.put(TraceContextHandshakeInterceptor.TRACE_CONTEXT_ATTRIBUTE,
                new TraceContext("11111111111111111111111111111111", "2222222222222222", null, true));
        Message<byte[]> connect = connectMessage(session,
                "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01");

        interceptor.beforeHandle(connect, null, null);

        TraceContext context = (TraceContext) session.get(TraceContextHandshakeInterceptor.TRACE_CONTEXT_ATTRIBUTE);
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", context.traceId());
        assertEquals("bbbbbbbbbbbbbbbb", context.spanId());
    }

    @Test
    void handlerExceptionMarksSpanError() {
        Message<byte[]> message = sendMessage(new HashMap<>(), "/app/chat.send", "fail");

        interceptor.beforeHandle(message, null, null);
        interceptor.afterMessageHandled(message, null, null, new IllegalStateException("boom"));

        assertEquals(1, recorded.size());
        Span span = recorded.get(0);
        assertEquals(Span.Status.ERROR, span.getStatus());
        assertEquals("IllegalStateException", span.getAttributes().get("error"));
        assertEquals("boom", span.getAttributes().get("error.message"));
    }

    private static Message<byte[]> connectMessage(Map<String, Object> session, String traceparent) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("s1");
        accessor.setSessionAttributes(session);
        accessor.addNativeHeader("traceparent", traceparent);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Message<byte[]> sendMessage(Map<String, Object> session, String destination, String payload) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId("s1");
        accessor.setSessionAttributes(session);
        accessor.setDestination(destination);
        return MessageBuilder.createMessage(payload.getBytes(StandardCharsets.UTF_8), accessor.getMessageHeaders());
    }
}
