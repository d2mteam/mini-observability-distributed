package com.core.demo;

import com.core.metrics.MetricsRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebSocketSessionMetricsListenerTest {
    @Test
    void duplicateDisconnectDoesNotCloseConnectionTwice() {
        CountingMetrics metrics = new CountingMetrics();
        WebSocketSessionMetricsListener listener = new WebSocketSessionMetricsListener(metrics, "WS /ws/chat");

        Message<byte[]> connectedMessage = message("s1", SimpMessageType.CONNECT_ACK);
        Message<byte[]> disconnectedMessage = message("s1", SimpMessageType.DISCONNECT);

        listener.onConnected(new SessionConnectedEvent(this, connectedMessage));
        listener.onConnected(new SessionConnectedEvent(this, connectedMessage));
        listener.onDisconnected(new SessionDisconnectEvent(this, disconnectedMessage, "s1", CloseStatus.NORMAL));
        listener.onDisconnected(new SessionDisconnectEvent(this, disconnectedMessage, "s1", CloseStatus.NORMAL));

        assertEquals(1, metrics.opened);
        assertEquals(1, metrics.closed);
    }

    private static Message<byte[]> message(String sessionId, SimpMessageType messageType) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(messageType);
        accessor.setSessionId(sessionId);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static class CountingMetrics implements MetricsRegistry {
        int opened;
        int closed;

        @Override
        public void onConnectionOpened(String endpoint) {
            opened++;
        }

        @Override
        public void onConnectionClosed(String endpoint) {
            closed++;
        }
    }
}
